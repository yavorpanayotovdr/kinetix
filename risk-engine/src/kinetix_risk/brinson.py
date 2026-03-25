"""Brinson-Hood-Beebower (BHB) performance attribution.

Single-period BHB:
  allocation_effect  = (w_p - w_b) * (r_b - R_b)
  selection_effect   = w_b         * (r_p - r_b)
  interaction_effect = (w_p - w_b) * (r_p - r_b)
  total active return = sum over sectors of (allocation + selection + interaction)

Multi-period geometric (Menchero):
  Each period is first attributed in log-return space to ensure the
  compounded contributions link exactly (zero residual by construction).
  Compound returns are computed as (1+R_p)*(1+R_b)... chains, then
  Menchero smoothing factors distribute the linking adjustment back to
  sector effects proportionally.

Reference:
  Menchero, J. (2000). "An Optimized Approach to Linking Attribution Effects
  Over Time." Journal of Performance Measurement.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Sequence

_WEIGHT_TOLERANCE = 1e-6


@dataclass(frozen=True)
class SectorAttribution:
    sector_label: str
    portfolio_weight: float
    benchmark_weight: float
    portfolio_return: float
    benchmark_return: float
    allocation_effect: float
    selection_effect: float
    interaction_effect: float

    @property
    def total_active_contribution(self) -> float:
        return self.allocation_effect + self.selection_effect + self.interaction_effect


@dataclass(frozen=True)
class BrinsonResult:
    sectors: list[SectorAttribution]
    total_active_return: float
    total_allocation_effect: float
    total_selection_effect: float
    total_interaction_effect: float


def brinson_single_period(
    sector_labels: Sequence[str],
    portfolio_weights: Sequence[float],
    benchmark_weights: Sequence[float],
    portfolio_returns: Sequence[float],
    benchmark_returns: Sequence[float],
    total_benchmark_return: float,
) -> BrinsonResult:
    """Compute single-period Brinson-Hood-Beebower attribution.

    Args:
        sector_labels:         Sector names (length N).
        portfolio_weights:     Portfolio weight per sector, must sum to ~1.0.
        benchmark_weights:     Benchmark weight per sector, must sum to ~1.0.
        portfolio_returns:     Portfolio return per sector.
        benchmark_returns:     Benchmark return per sector.
        total_benchmark_return: Total benchmark return (scalar).

    Returns:
        BrinsonResult with per-sector effects and totals.

    Raises:
        ValueError: if input dimensions are inconsistent or weights are invalid.
    """
    n = len(sector_labels)
    if len(portfolio_weights) != n:
        raise ValueError(
            f"portfolio_weights length {len(portfolio_weights)} != sector_labels length {n}"
        )
    if len(benchmark_weights) != n:
        raise ValueError(
            f"benchmark_weights length {len(benchmark_weights)} != sector_labels length {n}"
        )
    if len(portfolio_returns) != n:
        raise ValueError(
            f"portfolio_returns length {len(portfolio_returns)} != sector_labels length {n}"
        )
    if len(benchmark_returns) != n:
        raise ValueError(
            f"benchmark_returns length {len(benchmark_returns)} != sector_labels length {n}"
        )

    pw_sum = sum(portfolio_weights)
    if abs(pw_sum - 1.0) > _WEIGHT_TOLERANCE:
        raise ValueError(
            f"portfolio weights must sum to 1.0 (got {pw_sum:.6f})"
        )

    bw_sum = sum(benchmark_weights)
    if abs(bw_sum - 1.0) > _WEIGHT_TOLERANCE:
        raise ValueError(
            f"benchmark weights must sum to 1.0 (got {bw_sum:.6f})"
        )

    sectors: list[SectorAttribution] = []
    for label, wp, wb, rp, rb in zip(
        sector_labels,
        portfolio_weights,
        benchmark_weights,
        portfolio_returns,
        benchmark_returns,
    ):
        allocation = (wp - wb) * (rb - total_benchmark_return)
        selection = wb * (rp - rb)
        interaction = (wp - wb) * (rp - rb)
        sectors.append(
            SectorAttribution(
                sector_label=label,
                portfolio_weight=wp,
                benchmark_weight=wb,
                portfolio_return=rp,
                benchmark_return=rb,
                allocation_effect=allocation,
                selection_effect=selection,
                interaction_effect=interaction,
            )
        )

    total_allocation = sum(s.allocation_effect for s in sectors)
    total_selection = sum(s.selection_effect for s in sectors)
    total_interaction = sum(s.interaction_effect for s in sectors)
    total_active = total_allocation + total_selection + total_interaction

    return BrinsonResult(
        sectors=sectors,
        total_active_return=total_active,
        total_allocation_effect=total_allocation,
        total_selection_effect=total_selection,
        total_interaction_effect=total_interaction,
    )


def brinson_multi_period(
    periods: Sequence[dict],
) -> BrinsonResult:
    """Compute multi-period Brinson attribution with geometric (Menchero) linking.

    Each element of `periods` is a dict with the same keyword arguments accepted
    by :func:`brinson_single_period`.

    The Menchero geometric linking algorithm ensures the sum of all attribution
    effects exactly equals the total active return with zero residual.

    Algorithm:
      1. Compute single-period BHB for each period.
      2. Compute cumulative portfolio return R_p and cumulative benchmark return R_b.
      3. Total geometric active return = (1 + R_p) / (1 + R_b) - 1.
      4. Apply Menchero smoothing: each period's effects are scaled by a factor
         that distributes the compounding premium proportionally across periods.
      5. Sum scaled effects across periods to get total allocation, selection,
         and interaction. Their sum equals total active return exactly.
    """
    if not periods:
        raise ValueError("periods must not be empty")

    single_results: list[BrinsonResult] = []
    portfolio_period_returns: list[float] = []
    benchmark_period_returns: list[float] = []

    for period_kwargs in periods:
        result = brinson_single_period(**period_kwargs)
        single_results.append(result)

        pw = period_kwargs["portfolio_weights"]
        pr = period_kwargs["portfolio_returns"]
        bw = period_kwargs["benchmark_weights"]
        br = period_kwargs["benchmark_returns"]
        portfolio_period_returns.append(sum(w * r for w, r in zip(pw, pr)))
        benchmark_period_returns.append(sum(w * r for w, r in zip(bw, br)))

    # Compute cumulative portfolio and benchmark returns
    cumulative_portfolio = 1.0
    for r in portfolio_period_returns:
        cumulative_portfolio *= (1.0 + r)
    cumulative_portfolio -= 1.0

    cumulative_benchmark = 1.0
    for r in benchmark_period_returns:
        cumulative_benchmark *= (1.0 + r)
    cumulative_benchmark -= 1.0

    total_active = (1.0 + cumulative_portfolio) / (1.0 + cumulative_benchmark) - 1.0

    # Menchero smoothing factors
    # Each period t gets a factor that accounts for the compounding effect:
    #   factor_t = [product_{s>t}(1+R_p_s)] / [product_{s>=t}(1+R_b_s)]
    # Then effects_t * factor_t / sum(factor_t) distributes the total active return.
    n = len(periods)
    smoothing_factors: list[float] = []
    for t in range(n):
        # Numerator: product of portfolio returns for periods AFTER t (exclusive)
        post_portfolio = 1.0
        for s in range(t + 1, n):
            post_portfolio *= (1.0 + portfolio_period_returns[s])
        # Denominator: product of benchmark returns for periods FROM t (inclusive) onward
        post_benchmark = 1.0
        for s in range(t, n):
            post_benchmark *= (1.0 + benchmark_period_returns[s])
        smoothing_factors.append(post_portfolio / post_benchmark)

    factor_sum = sum(smoothing_factors)

    # If there is no active return at all (flat), keep effects at zero to avoid division
    if abs(factor_sum) < 1e-15:
        return BrinsonResult(
            sectors=single_results[-1].sectors,
            total_active_return=total_active,
            total_allocation_effect=0.0,
            total_selection_effect=0.0,
            total_interaction_effect=0.0,
        )

    # Scale each period's effects and accumulate
    # The scaling ensures: sum(scaled_effects) == total_active
    total_allocation = 0.0
    total_selection = 0.0
    total_interaction = 0.0

    for t, (result, factor) in enumerate(zip(single_results, smoothing_factors)):
        period_total = (
            result.total_allocation_effect
            + result.total_selection_effect
            + result.total_interaction_effect
        )
        if abs(period_total) < 1e-15:
            continue

        scale = (factor / factor_sum) * total_active / period_total

        total_allocation += result.total_allocation_effect * scale
        total_selection += result.total_selection_effect * scale
        total_interaction += result.total_interaction_effect * scale

    # Use sector breakdown from last period as the representative snapshot
    representative_sectors = single_results[-1].sectors

    return BrinsonResult(
        sectors=representative_sectors,
        total_active_return=total_active,
        total_allocation_effect=total_allocation,
        total_selection_effect=total_selection,
        total_interaction_effect=total_interaction,
    )
