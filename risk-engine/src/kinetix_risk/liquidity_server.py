"""gRPC servicer for Liquidity-Adjusted VaR calculations.

This module handles the LiquidityRiskService gRPC contract, translating
proto messages to domain calls in liquidity.py and mapping results back.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone

import grpc
from google.protobuf import timestamp_pb2

from kinetix.common import types_pb2
from kinetix.risk import liquidity_pb2, liquidity_pb2_grpc
from kinetix_risk.liquidity import (
    LiquidityInput,
    LiquidityTier,
    assess_concentration_flag,
    compute_liquidation_horizon,
    compute_lvar,
    compute_stressed_liquidation_value,
)
from kinetix_risk.models import AssetClass

logger = logging.getLogger(__name__)

# Mapping from proto AssetClass enum int to domain AssetClass enum.
_PROTO_ASSET_CLASS_TO_DOMAIN: dict[int, AssetClass] = {
    types_pb2.EQUITY: AssetClass.EQUITY,
    types_pb2.FIXED_INCOME: AssetClass.FIXED_INCOME,
    types_pb2.FX: AssetClass.FX,
    types_pb2.COMMODITY: AssetClass.COMMODITY,
    types_pb2.DERIVATIVE: AssetClass.DERIVATIVE,
}

# Mapping from domain LiquidityTier to proto LiquidityTier enum int.
_DOMAIN_TIER_TO_PROTO: dict[LiquidityTier, int] = {
    LiquidityTier.HIGH_LIQUID: liquidity_pb2.HIGH_LIQUID,
    LiquidityTier.LIQUID: liquidity_pb2.LIQUID,
    LiquidityTier.SEMI_LIQUID: liquidity_pb2.SEMI_LIQUID,
    LiquidityTier.ILLIQUID: liquidity_pb2.ILLIQUID,
}

# Status ordering for "worst" aggregation.
_STATUS_ORDER = {"OK": 0, "WARNING": 1, "BREACHED": 2}

# Default ADV concentration limit (50% of ADV); per-position limit can be
# extended to use per-instrument limits fetched from reference-data.
_DEFAULT_ADV_CONCENTRATION_LIMIT_PCT = 0.50


def _proto_to_domain_asset_class(proto_ac: int) -> AssetClass:
    return _PROTO_ASSET_CLASS_TO_DOMAIN.get(proto_ac, AssetClass.EQUITY)


def _now_timestamp() -> timestamp_pb2.Timestamp:
    now = datetime.now(timezone.utc)
    ts = timestamp_pb2.Timestamp()
    ts.FromDatetime(now)
    return ts


def _worst_status(statuses: list[str]) -> str:
    if not statuses:
        return "OK"
    return max(statuses, key=lambda s: _STATUS_ORDER.get(s, 0))


class LiquidityAdjustedVaRServicer(
    liquidity_pb2_grpc.LiquidityRiskServiceServicer
):
    """gRPC servicer that computes liquidity-adjusted VaR for a portfolio."""

    def CalculateLiquidityAdjustedVaR(self, request, context):
        try:
            if request.base_holding_period <= 0:
                raise ValueError("base_holding_period must be > 0")

            domain_inputs: list[LiquidityInput] = [
                LiquidityInput(
                    instrument_id=inp.instrument_id,
                    market_value=inp.market_value,
                    adv=None if inp.adv_missing else inp.adv,
                    adv_staleness_days=inp.adv_staleness_days,
                    asset_class=_proto_to_domain_asset_class(inp.asset_class),
                )
                for inp in request.inputs
            ]

            # Determine the worst liquidation horizon across all positions
            # to use for portfolio-level LVaR (conservative approach).
            horizon_results = [
                compute_liquidation_horizon(
                    market_value=inp.market_value,
                    adv=inp.adv,
                    adv_staleness_days=inp.adv_staleness_days,
                )
                for inp in domain_inputs
            ]

            max_horizon = max(
                (h.horizon_days for h in horizon_results),
                default=request.base_holding_period,
            )

            lvar_result = compute_lvar(
                base_var=request.base_var,
                liquidation_horizon_days=max_horizon,
                base_holding_period=request.base_holding_period,
                inputs=domain_inputs,
            )

            # Build per-position risk entries
            position_risks = []
            concentration_statuses = []

            for inp, h_result in zip(domain_inputs, horizon_results):
                proto_ac = _proto_to_domain_asset_class(
                    next(
                        (pi.asset_class for pi in request.inputs if pi.instrument_id == inp.instrument_id),
                        types_pb2.EQUITY,
                    )
                )
                # LVaR contribution: position weight * portfolio LVaR
                total_mv = sum(abs(i.market_value) for i in domain_inputs)
                position_weight = abs(inp.market_value) / total_mv if total_mv > 0 else 0.0
                lvar_contribution = lvar_result.lvar_value * position_weight

                # Stressed liquidation value
                ac_name = inp.asset_class.value
                stress_factor = request.stress_factors.get(ac_name, 0.0)
                stressed_value = compute_stressed_liquidation_value(
                    market_value=inp.market_value,
                    horizon_days=h_result.horizon_days,
                    daily_vol=request.portfolio_daily_vol or 0.015,
                    stress_factor=stress_factor,
                )

                # Concentration check
                conc = assess_concentration_flag(
                    market_value=inp.market_value,
                    adv=inp.adv,
                    limit_pct=_DEFAULT_ADV_CONCENTRATION_LIMIT_PCT,
                    adv_staleness_days=inp.adv_staleness_days,
                )
                concentration_statuses.append(conc.status)

                position_risks.append(
                    liquidity_pb2.PositionLiquidityRisk(
                        instrument_id=inp.instrument_id,
                        asset_class=next(
                            (pi.asset_class for pi in request.inputs if pi.instrument_id == inp.instrument_id),
                            types_pb2.EQUITY,
                        ),
                        market_value=inp.market_value,
                        tier=_DOMAIN_TIER_TO_PROTO[h_result.tier],
                        horizon_days=h_result.horizon_days,
                        adv=inp.adv or 0.0,
                        adv_missing=h_result.adv_missing,
                        adv_stale=h_result.adv_stale,
                        lvar_contribution=lvar_contribution,
                        stressed_liquidation_value=stressed_value,
                        concentration_status=conc.status,
                    )
                )

            portfolio_status = _worst_status(concentration_statuses)

            return liquidity_pb2.LiquidityAdjustedVaRResponse(
                book_id=request.book_id,
                portfolio_lvar=lvar_result.lvar_value,
                data_completeness=lvar_result.data_completeness,
                position_risks=position_risks,
                portfolio_concentration_status=portfolio_status,
                calculated_at=_now_timestamp(),
            )

        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
        except Exception as e:
            logger.exception("CalculateLiquidityAdjustedVaR failed")
            context.abort(grpc.StatusCode.INTERNAL, str(e))
