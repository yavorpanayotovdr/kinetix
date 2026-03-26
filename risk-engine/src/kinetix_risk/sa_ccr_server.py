"""gRPC servicer for SA-CCR (BCBS 279) regulatory capital calculations.

Translates proto SaCcrPositionInput messages to domain objects, delegates to
sa_ccr.py for the deterministic BCBS 279 calculation, and maps results back.

SA-CCR is the REGULATORY capital model — distinct from the Monte Carlo PFE
model in credit_exposure.py.  Never conflate the two.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone

import grpc
from google.protobuf import timestamp_pb2

from kinetix.risk import counterparty_risk_pb2, counterparty_risk_pb2_grpc
from kinetix_risk.models import (
    AssetClass,
    FxPosition,
    OptionPosition,
    OptionType,
    PositionRisk,
    SwapPosition,
)
from kinetix_risk.sa_ccr import SaCcrResult, calculate_sa_ccr

logger = logging.getLogger(__name__)


def _now_timestamp() -> timestamp_pb2.Timestamp:
    now = datetime.now(timezone.utc)
    ts = timestamp_pb2.Timestamp()
    ts.FromDatetime(now)
    return ts


def _proto_position_to_domain(
    proto: counterparty_risk_pb2.SaCcrPositionInput,
) -> PositionRisk | OptionPosition:
    """Convert a SaCcrPositionInput proto to a domain position.

    Option positions are converted to OptionPosition; all others to the
    appropriate PositionRisk subclass.
    """
    if proto.is_option:
        option_type = OptionType.PUT if proto.option_type.upper() == "PUT" else OptionType.CALL
        ac = _proto_asset_class(proto.asset_class)
        return OptionPosition(
            instrument_id=proto.instrument_id,
            underlying_id=proto.instrument_id,
            option_type=option_type,
            strike=proto.strike if proto.strike > 0.0 else 100.0,
            expiry_days=proto.expiry_days if proto.expiry_days > 0 else 365,
            spot_price=proto.spot_price if proto.spot_price > 0.0 else 100.0,
            implied_vol=proto.implied_vol if proto.implied_vol > 0.0 else 0.20,
            risk_free_rate=0.05,
            quantity=proto.quantity if proto.quantity > 0.0 else 1.0,
            currency=proto.currency or "USD",
            asset_class=ac,
        )

    ac = _proto_asset_class(proto.asset_class)

    if proto.asset_class.upper() == "IR":
        return SwapPosition(
            instrument_id=proto.instrument_id,
            asset_class=ac,
            market_value=proto.market_value,
            currency=proto.currency or "USD",
            notional=proto.notional,
            maturity_date=proto.maturity_date or "",
            pay_receive=proto.pay_receive or "PAY_FIXED",
        )

    if proto.asset_class.upper() == "FX":
        parts = proto.instrument_id.split("/")
        base_currency = parts[0] if len(parts) == 2 else proto.currency
        quote_currency = parts[1] if len(parts) == 2 else "USD"
        return FxPosition(
            instrument_id=proto.instrument_id,
            asset_class=ac,
            market_value=proto.market_value,
            currency=proto.currency or "USD",
            base_currency=base_currency,
            quote_currency=quote_currency,
        )

    # Preserve credit subtype for SA-CCR supervisory factor lookup (BCBS 279 Table 2)
    credit_subtype = proto.asset_class.upper() if proto.asset_class.upper() in ("CREDIT_IG", "CREDIT_HY") else None

    return PositionRisk(
        instrument_id=proto.instrument_id,
        asset_class=ac,
        market_value=proto.market_value,
        currency=proto.currency or "USD",
        credit_subtype=credit_subtype,
    )


def _proto_asset_class(asset_class_str: str) -> AssetClass:
    mapping = {
        "IR": AssetClass.FIXED_INCOME,
        "FIXED_INCOME": AssetClass.FIXED_INCOME,
        "FX": AssetClass.FX,
        "EQUITY": AssetClass.EQUITY,
        "COMMODITY": AssetClass.COMMODITY,
        "CREDIT_IG": AssetClass.FIXED_INCOME,  # IG credit mapped to fixed income for domain model
        "CREDIT_HY": AssetClass.FIXED_INCOME,  # HY credit mapped to fixed income for domain model
    }
    return mapping.get(asset_class_str.upper(), AssetClass.EQUITY)


class SaCcrServicer(counterparty_risk_pb2_grpc.SaCcrServiceServicer):
    """gRPC servicer for SA-CCR (BCBS 279) regulatory EAD calculation.

    This servicer is stateless.  All calculation logic lives in sa_ccr.py.
    """

    def CalculateSaCcr(self, request, context):
        try:
            if not request.netting_set_id:
                raise ValueError("netting_set_id is required")

            positions = [_proto_position_to_domain(p) for p in request.positions]
            collateral_net = request.collateral_net

            result: SaCcrResult = calculate_sa_ccr(
                positions=positions,
                market_data={},
                collateral_net=collateral_net,
            )

            return counterparty_risk_pb2.CalculateSaCcrResponse(
                netting_set_id=request.netting_set_id,
                counterparty_id=request.counterparty_id,
                replacement_cost=result.replacement_cost,
                pfe_addon=result.pfe_addon,
                multiplier=result.multiplier,
                ead=result.ead,
                alpha=result.alpha,
                calculated_at=_now_timestamp(),
            )

        except ValueError as e:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(e))
        except Exception as e:
            logger.exception("CalculateSaCcr failed")
            context.abort(grpc.StatusCode.INTERNAL, str(e))
