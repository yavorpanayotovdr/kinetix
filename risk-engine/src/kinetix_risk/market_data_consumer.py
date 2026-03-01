import math
from collections import defaultdict
from dataclasses import dataclass, field

import numpy as np

from kinetix_risk.market_data_models import VolSurface, VolSurfacePoint, YieldCurveData
from kinetix_risk.models import AssetClass
from kinetix_risk.volatility import VolatilityProvider


@dataclass(frozen=True)
class MarketDataBundle:
    volatility_provider: VolatilityProvider | None = None
    correlation_matrix: np.ndarray | None = None
    spot_prices: dict[str, float] = field(default_factory=dict)
    vol_surfaces: dict[str, VolSurface] = field(default_factory=dict)
    yield_curves: dict[str, YieldCurveData] = field(default_factory=dict)


_ASSET_CLASS_NAME_TO_DOMAIN = {ac.value: ac for ac in AssetClass}


def _annualized_vol_from_prices(prices: list[float]) -> float | None:
    if len(prices) < 2:
        return None
    log_returns = [math.log(prices[i] / prices[i - 1]) for i in range(1, len(prices))]
    return float(np.std(log_returns, ddof=1)) * math.sqrt(252)


def consume_market_data(market_data: list[dict]) -> MarketDataBundle:
    if not market_data:
        return MarketDataBundle()

    spot_prices: dict[str, float] = {}
    vols_by_asset_class: dict[AssetClass, list[float]] = defaultdict(list)
    correlation_matrix: np.ndarray | None = None
    vol_surfaces: dict[str, VolSurface] = {}
    yield_curves: dict[str, YieldCurveData] = {}

    for item in market_data:
        data_type = item.get("data_type")

        if data_type == "SPOT_PRICE":
            instrument_id = item.get("instrument_id", "")
            scalar = item.get("scalar")
            if instrument_id and scalar is not None:
                spot_prices[instrument_id] = scalar

        elif data_type == "HISTORICAL_PRICES":
            asset_class_name = item.get("asset_class", "")
            ac = _ASSET_CLASS_NAME_TO_DOMAIN.get(asset_class_name)
            if ac is None:
                continue
            ts = item.get("time_series", [])
            prices = [pt["value"] for pt in ts]
            vol = _annualized_vol_from_prices(prices)
            if vol is not None:
                vols_by_asset_class[ac].append(vol)

        elif data_type == "CORRELATION_MATRIX":
            matrix_data = item.get("matrix")
            if matrix_data:
                rows = matrix_data["rows"]
                cols = matrix_data["cols"]
                values = matrix_data["values"]
                correlation_matrix = np.array(values).reshape(rows, cols)

        elif data_type == "VOLATILITY_SURFACE":
            instrument_id = item.get("instrument_id", "")
            raw_points = item.get("points", [])
            if instrument_id and raw_points:
                points = [
                    VolSurfacePoint(
                        strike=pt["strike"],
                        maturity_days=pt["maturity_days"],
                        implied_vol=pt["implied_vol"],
                    )
                    for pt in raw_points
                ]
                vol_surfaces[instrument_id] = VolSurface(points=points)

        elif data_type == "YIELD_CURVE":
            currency = item.get("currency", "")
            raw_tenors = item.get("tenors", [])
            if currency and raw_tenors:
                tenors = [(t["days"], t["rate"]) for t in raw_tenors]
                yield_curves[currency] = YieldCurveData(tenors=tenors)

    volatility_provider = None
    if vols_by_asset_class:
        avg_vols = {
            ac: sum(vols) / len(vols)
            for ac, vols in vols_by_asset_class.items()
        }
        volatility_provider = VolatilityProvider.from_dict(avg_vols)

    return MarketDataBundle(
        volatility_provider=volatility_provider,
        correlation_matrix=correlation_matrix,
        spot_prices=spot_prices,
        vol_surfaces=vol_surfaces,
        yield_curves=yield_curves,
    )
