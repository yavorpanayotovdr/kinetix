"""gRPC servicer for factor-based risk decomposition.

Handles the DecomposeFactorRisk RPC on RiskCalculationService,
translating proto messages to domain calls in factor_model.py.
"""

from __future__ import annotations

import logging

import grpc
import numpy as np

from kinetix.risk import risk_calculation_pb2
from kinetix_risk.bond_pricing import bond_dv01
from kinetix_risk.factor_model import (
    EQUITY_BETA,
    RATES_DURATION,
    CREDIT_SPREAD,
    FX_DELTA,
    VOL_EXPOSURE,
    ALL_FACTORS,
    FactorType,
    InstrumentLoading,
    LoadingMethod,
    MIN_HISTORY_FOR_OLS,
    compute_factor_pnl_attribution,
    decompose_factor_risk,
    estimate_analytical_loading,
    estimate_loading,
    estimate_ols_loading,
)
from kinetix_risk.models import (
    AssetClass,
    BondPosition,
    FxPosition,
    OptionPosition,
    OptionType,
    PositionRisk,
    SwapPosition,
)

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Proto enum <-> domain mappings
# ---------------------------------------------------------------------------

_PROTO_FACTOR_TO_DOMAIN: dict[int, FactorType] = {
    risk_calculation_pb2.FACTOR_EQUITY_BETA: EQUITY_BETA,
    risk_calculation_pb2.FACTOR_RATES_DURATION: RATES_DURATION,
    risk_calculation_pb2.FACTOR_CREDIT_SPREAD: CREDIT_SPREAD,
    risk_calculation_pb2.FACTOR_FX_DELTA: FX_DELTA,
    risk_calculation_pb2.FACTOR_VOL_EXPOSURE: VOL_EXPOSURE,
}

_DOMAIN_FACTOR_TO_PROTO: dict[FactorType, int] = {
    v: k for k, v in _PROTO_FACTOR_TO_DOMAIN.items()
}

_PROTO_METHOD_TO_DOMAIN: dict[int, LoadingMethod] = {
    risk_calculation_pb2.LOADING_OLS_REGRESSION: LoadingMethod.OLS_REGRESSION,
    risk_calculation_pb2.LOADING_ANALYTICAL: LoadingMethod.ANALYTICAL,
    risk_calculation_pb2.LOADING_MANUAL: LoadingMethod.MANUAL,
}

_DOMAIN_METHOD_TO_PROTO: dict[LoadingMethod, int] = {
    v: k for k, v in _PROTO_METHOD_TO_DOMAIN.items()
}

_ASSET_CLASS_STR_TO_DOMAIN: dict[str, AssetClass] = {
    "EQUITY": AssetClass.EQUITY,
    "FIXED_INCOME": AssetClass.FIXED_INCOME,
    "FX": AssetClass.FX,
    "COMMODITY": AssetClass.COMMODITY,
    "DERIVATIVE": AssetClass.DERIVATIVE,
}


# ---------------------------------------------------------------------------
# Helper: build a domain PositionRisk from a proto PositionLoadingInput
# ---------------------------------------------------------------------------


def _proto_position_to_domain(proto_pos: risk_calculation_pb2.PositionLoadingInput) -> PositionRisk:
    """Convert a PositionLoadingInput proto to a domain PositionRisk.

    For the factor decomposition the position type matters for analytical
    loadings. We use the asset class string to pick the right subtype when
    we don't have the full instrument details.
    """
    ac = _ASSET_CLASS_STR_TO_DOMAIN.get(proto_pos.asset_class, AssetClass.EQUITY)

    if ac == AssetClass.FIXED_INCOME:
        return BondPosition(
            instrument_id=proto_pos.instrument_id,
            asset_class=ac,
            market_value=proto_pos.market_value,
            currency="USD",
            face_value=abs(proto_pos.market_value),  # approximation
            coupon_rate=0.05,
            maturity_date="2034-01-01",  # ~10y default for DV01
        )

    if ac == AssetClass.FX:
        return FxPosition(
            instrument_id=proto_pos.instrument_id,
            asset_class=ac,
            market_value=proto_pos.market_value,
            currency="USD",
        )

    return PositionRisk(
        instrument_id=proto_pos.instrument_id,
        asset_class=ac,
        market_value=proto_pos.market_value,
        currency="USD",
    )


# ---------------------------------------------------------------------------
# Servicer
# ---------------------------------------------------------------------------


class FactorDecompositionServicer:
    """Handles DecomposeFactorRisk gRPC calls."""

    def DecomposeFactorRisk(
        self,
        request: risk_calculation_pb2.FactorDecompositionRequest,
        context,
    ) -> risk_calculation_pb2.FactorDecompositionResponse:
        try:
            return self._decompose(request)
        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
            return risk_calculation_pb2.FactorDecompositionResponse()
        except Exception as e:
            logger.exception("DecomposeFactorRisk failed")
            context.abort(grpc.StatusCode.INTERNAL, str(e))
            return risk_calculation_pb2.FactorDecompositionResponse()

    def _decompose(
        self, request: risk_calculation_pb2.FactorDecompositionRequest
    ) -> risk_calculation_pb2.FactorDecompositionResponse:
        book_id = request.book_id
        total_var = request.total_var
        decomposition_date = request.decomposition_date or ""
        job_id = request.job_id or ""

        # Build factor return arrays
        factor_returns_by_factor: dict[FactorType, np.ndarray] = {}
        for fr in request.factor_returns:
            domain_factor = _PROTO_FACTOR_TO_DOMAIN.get(fr.factor)
            if domain_factor is not None:
                factor_returns_by_factor[domain_factor] = np.array(list(fr.returns), dtype=float)

        # Convert positions to domain objects
        positions: list[PositionRisk] = [
            _proto_position_to_domain(p) for p in request.positions
        ]

        # Estimate loadings for each (position, factor) combination
        all_loadings: dict[str, list[InstrumentLoading]] = {}
        loading_results: list[InstrumentLoading] = []

        for proto_pos, domain_pos in zip(request.positions, positions):
            instrument_id = proto_pos.instrument_id
            inst_returns = np.array(list(proto_pos.instrument_returns), dtype=float)
            pos_loadings: list[InstrumentLoading] = []

            for domain_factor, factor_returns in factor_returns_by_factor.items():
                loading = estimate_loading(
                    position=domain_pos,
                    factor=domain_factor,
                    instrument_returns=inst_returns,
                    factor_returns=factor_returns,
                )
                loading = InstrumentLoading(
                    instrument_id=instrument_id,
                    factor=domain_factor,
                    loading=loading.loading,
                    r_squared=loading.r_squared,
                    method=loading.method,
                )
                pos_loadings.append(loading)
                loading_results.append(loading)

            # For factors with no return series, use analytical fallback
            for factor in ALL_FACTORS:
                if factor not in factor_returns_by_factor:
                    analytical = estimate_analytical_loading(domain_pos, factor)
                    analytical = InstrumentLoading(
                        instrument_id=instrument_id,
                        factor=factor,
                        loading=analytical.loading,
                        r_squared=None,
                        method=LoadingMethod.ANALYTICAL,
                    )
                    if analytical.loading != 0.0:
                        pos_loadings.append(analytical)
                        loading_results.append(analytical)

            all_loadings[instrument_id] = pos_loadings

        # Run decomposition
        result = decompose_factor_risk(
            book_id=book_id,
            positions=positions,
            loadings=all_loadings,
            factor_returns_by_factor=factor_returns_by_factor,
            total_var=total_var,
            decomposition_date=decomposition_date,
            job_id=job_id or None,
        )

        # P&L attribution: computed when total_pnl and factor_returns_today are supplied.
        total_pnl = request.total_pnl
        factor_returns_today_proto = dict(request.factor_returns_today)
        idiosyncratic_pnl = 0.0
        pnl_attribution_by_factor: dict[FactorType, float] = {}

        if total_pnl != 0.0 and factor_returns_today_proto:
            factor_returns_today_domain: dict[FactorType, float] = {}
            for name, ret in factor_returns_today_proto.items():
                try:
                    domain_factor = FactorType(name)
                    factor_returns_today_domain[domain_factor] = ret
                except ValueError:
                    logger.warning("Unknown factor name in factor_returns_today: %s", name)

            if factor_returns_today_domain:
                pnl_result = compute_factor_pnl_attribution(
                    book_id=book_id,
                    positions=positions,
                    loadings=all_loadings,
                    factor_returns_today=factor_returns_today_domain,
                    total_pnl=total_pnl,
                    attribution_date=decomposition_date,
                )
                idiosyncratic_pnl = pnl_result.idiosyncratic_pnl
                pnl_attribution_by_factor = {
                    fc.factor: fc.pnl_attribution for fc in pnl_result.factor_pnl
                }

        # Build response
        proto_contributions = [
            risk_calculation_pb2.FactorContribution(
                factor=_DOMAIN_FACTOR_TO_PROTO.get(fc.factor, 0),
                factor_exposure=fc.factor_exposure,
                factor_var=fc.factor_var,
                pnl_attribution=pnl_attribution_by_factor.get(fc.factor, 0.0),
                pct_of_total_var=fc.pct_of_total_var,
            )
            for fc in result.factor_contributions
        ]

        proto_loadings = [
            risk_calculation_pb2.InstrumentLoadingResult(
                instrument_id=il.instrument_id,
                factor=_DOMAIN_FACTOR_TO_PROTO.get(il.factor, 0),
                loading=il.loading,
                r_squared=il.r_squared if il.r_squared is not None else 0.0,
                has_r_squared=il.r_squared is not None,
                method=_DOMAIN_METHOD_TO_PROTO.get(il.method, 0),
            )
            for il in loading_results
        ]

        return risk_calculation_pb2.FactorDecompositionResponse(
            book_id=book_id,
            decomposition_date=result.decomposition_date,
            total_var=result.total_var,
            systematic_var=result.systematic_var,
            idiosyncratic_var=result.idiosyncratic_var,
            r_squared=result.r_squared,
            factor_contributions=proto_contributions,
            loadings=proto_loadings,
            job_id=job_id,
            idiosyncratic_pnl=idiosyncratic_pnl,
        )
