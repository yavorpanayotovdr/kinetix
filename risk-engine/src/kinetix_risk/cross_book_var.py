from collections import defaultdict

import numpy as np

from kinetix_risk.models import (
    AssetClass,
    BookVaRContribution,
    CalculationType,
    ConfidenceLevel,
    CrossBookVaRResult,
    PositionRisk,
    StressedDiversificationResult,
    VaRResult,
)
from kinetix_risk.portfolio_risk import calculate_portfolio_var
from kinetix_risk.volatility import VolatilityProvider


def calculate_cross_book_var(
    books: dict[str, list[PositionRisk]],
    calculation_type: CalculationType,
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    num_simulations: int = 10_000,
    volatility_provider: VolatilityProvider | None = None,
    correlation_matrix: "np.ndarray | None" = None,
    risk_free_rate: float = 0.0,
    historical_returns: "np.ndarray | None" = None,
    correlation_method: str | None = None,
    seed: int | None = None,
) -> CrossBookVaRResult:
    """Calculate cross-book aggregated VaR with Euler allocation back to books."""
    # Filter out empty books for the merged calculation
    non_empty_books = {bid: pos for bid, pos in books.items() if pos}
    all_positions = [p for positions in non_empty_books.values() for p in positions]

    if not all_positions:
        raise ValueError("Cannot calculate cross-book VaR: no positions in any book")

    common_kwargs = dict(
        calculation_type=calculation_type,
        confidence_level=confidence_level,
        time_horizon_days=time_horizon_days,
        num_simulations=num_simulations,
        volatility_provider=volatility_provider,
        correlation_matrix=correlation_matrix,
        risk_free_rate=risk_free_rate,
        historical_returns=historical_returns,
        correlation_method=correlation_method,
        seed=seed,
    )

    # Aggregate VaR across all books
    aggregate_result = calculate_portfolio_var(positions=all_positions, **common_kwargs)

    # Standalone VaR per non-empty book
    standalone_vars: dict[str, float] = {}
    for book_id, positions in non_empty_books.items():
        standalone = calculate_portfolio_var(positions=positions, **common_kwargs)
        standalone_vars[book_id] = standalone.var_value

    total_standalone_var = sum(standalone_vars.values())
    diversification_benefit = total_standalone_var - aggregate_result.var_value

    # Incremental VaR: for each non-empty book, VaR(all) - VaR(all except this book)
    incremental_vars: dict[str, float] = {}
    non_empty_book_ids = list(non_empty_books.keys())
    for book_id in non_empty_book_ids:
        remaining_books = {bid: pos for bid, pos in non_empty_books.items() if bid != book_id}
        remaining_positions = [p for positions in remaining_books.values() for p in positions]
        if remaining_positions:
            subset_result = calculate_portfolio_var(positions=remaining_positions, **common_kwargs)
            incremental_vars[book_id] = aggregate_result.var_value - subset_result.var_value
        else:
            # Only one non-empty book — removing it removes everything
            incremental_vars[book_id] = aggregate_result.var_value

    # Decompose aggregate VaR back to book contributions
    book_contributions = decompose_book_contributions(
        books=books,
        aggregate_result=aggregate_result,
        standalone_vars=standalone_vars,
        incremental_vars=incremental_vars,
    )

    return CrossBookVaRResult(
        var_result=aggregate_result,
        book_contributions=book_contributions,
        total_standalone_var=total_standalone_var,
        diversification_benefit=diversification_benefit,
    )


def decompose_book_contributions(
    books: dict[str, list[PositionRisk]],
    aggregate_result: VaRResult,
    standalone_vars: dict[str, float],
    incremental_vars: dict[str, float] | None = None,
) -> list[BookVaRContribution]:
    """Decompose aggregate VaR into per-book contributions via Euler allocation.

    For each asset class in the component breakdown, determine each book's share
    of that asset class's total market value and allocate that asset class's VaR
    contribution to books proportionally.
    """
    aggregate_var = aggregate_result.var_value

    # Build per-book, per-asset-class absolute market value totals
    book_ac_mv: dict[str, dict[AssetClass, float]] = {}
    for book_id, positions in books.items():
        ac_mv: dict[AssetClass, float] = defaultdict(float)
        for p in positions:
            ac_mv[p.asset_class] += abs(p.market_value)
        book_ac_mv[book_id] = dict(ac_mv)

    # For each asset class in the breakdown, compute total absolute market value
    ac_total_abs_mv: dict[AssetClass, float] = defaultdict(float)
    for ac_mv in book_ac_mv.values():
        for ac, mv in ac_mv.items():
            ac_total_abs_mv[ac] += mv

    # Allocate each component's VaR contribution to books proportionally
    book_var_contribution: dict[str, float] = defaultdict(float)
    for component in aggregate_result.component_breakdown:
        ac = component.asset_class
        total_mv = ac_total_abs_mv.get(ac, 0.0)
        if total_mv == 0.0:
            continue
        for book_id in books:
            book_mv = book_ac_mv.get(book_id, {}).get(ac, 0.0)
            share = book_mv / total_mv
            book_var_contribution[book_id] += component.var_contribution * share

    # Compute total absolute market value per book for marginal VaR
    book_total_abs_mv: dict[str, float] = {}
    for book_id, ac_mv in book_ac_mv.items():
        book_total_abs_mv[book_id] = sum(ac_mv.values())

    # Build contribution objects
    contributions = []
    for book_id in sorted(books.keys()):
        var_contrib = book_var_contribution.get(book_id, 0.0)
        pct = (var_contrib / aggregate_var * 100.0) if aggregate_var > 0 else 0.0
        sa_var = standalone_vars.get(book_id, 0.0)
        div_benefit = sa_var - var_contrib
        book_mv = book_total_abs_mv.get(book_id, 0.0)
        marginal = (var_contrib / book_mv) if book_mv > 0 else 0.0

        incr_var = (incremental_vars or {}).get(book_id, 0.0)

        contributions.append(BookVaRContribution(
            book_id=book_id,
            var_contribution=var_contrib,
            percentage_of_total=pct,
            standalone_var=sa_var,
            diversification_benefit=div_benefit,
            marginal_var=marginal,
            incremental_var=incr_var,
        ))

    return contributions


def _build_stressed_correlation_matrix(n: int, stress_correlation: float) -> np.ndarray:
    """Build an n x n correlation matrix with all off-diagonal entries set to ``stress_correlation``."""
    stressed = np.full((n, n), stress_correlation)
    np.fill_diagonal(stressed, 1.0)
    return stressed


def calculate_stressed_cross_book_var(
    books: dict[str, list[PositionRisk]],
    calculation_type: CalculationType,
    confidence_level: ConfidenceLevel,
    time_horizon_days: int,
    stress_correlation: float = 0.9,
    num_simulations: int = 10_000,
    volatility_provider: VolatilityProvider | None = None,
    risk_free_rate: float = 0.0,
    historical_returns: "np.ndarray | None" = None,
    correlation_method: str | None = None,
    seed: int | None = None,
) -> StressedDiversificationResult:
    """Calculate cross-book VaR under both normal and stressed correlations.

    The stressed scenario replaces all off-diagonal correlation entries with
    ``stress_correlation`` (diagonal stays 1.0), demonstrating how diversification
    benefit can evaporate when correlations spike to crisis levels.

    Per-book standalone VaRs are also recalculated under stress so that the
    diversification benefit comparison is consistent (stressed benefit =
    sum-of-stressed-standalones minus stressed-aggregate).

    Because the stressed correlation matrix must be sized to match the number of
    asset classes in each individual VaR call (aggregate vs per-book standalone),
    the stressed calculation builds correctly-sized matrices per call rather than
    passing a single matrix through ``calculate_cross_book_var``.
    """
    common_kwargs = dict(
        books=books,
        calculation_type=calculation_type,
        confidence_level=confidence_level,
        time_horizon_days=time_horizon_days,
        num_simulations=num_simulations,
        volatility_provider=volatility_provider,
        risk_free_rate=risk_free_rate,
        historical_returns=historical_returns,
        correlation_method=correlation_method,
        seed=seed,
    )

    # Base result with normal correlations (no override — uses default matrix)
    base_result = calculate_cross_book_var(**common_kwargs)

    # For the stressed result, build correctly-sized stressed correlation
    # matrices for each individual VaR call.  calculate_cross_book_var passes
    # correlation_matrix to every call including per-book standalones, which may
    # have fewer asset classes than the aggregate.  To avoid dimension mismatches
    # we replicate the aggregate + standalone logic here with per-call matrices.
    non_empty_books = {bid: pos for bid, pos in books.items() if pos}
    all_positions = [p for positions in non_empty_books.values() for p in positions]

    if not all_positions:
        raise ValueError("Cannot calculate stressed cross-book VaR: no positions in any book")

    var_kwargs = dict(
        calculation_type=calculation_type,
        confidence_level=confidence_level,
        time_horizon_days=time_horizon_days,
        num_simulations=num_simulations,
        volatility_provider=volatility_provider,
        risk_free_rate=risk_free_rate,
        historical_returns=historical_returns,
        correlation_method=correlation_method,
        seed=seed,
    )

    # Aggregate stressed VaR — matrix sized to the number of distinct asset classes
    n_agg = len({p.asset_class for p in all_positions})
    stressed_agg_corr = _build_stressed_correlation_matrix(n_agg, stress_correlation)
    stressed_agg_result = calculate_portfolio_var(
        positions=all_positions,
        correlation_matrix=stressed_agg_corr,
        **var_kwargs,
    )

    # Standalone stressed VaR per non-empty book — matrix sized per book
    stressed_standalone_vars: dict[str, float] = {}
    for book_id, positions in non_empty_books.items():
        n_book = len({p.asset_class for p in positions})
        stressed_book_corr = _build_stressed_correlation_matrix(n_book, stress_correlation)
        standalone = calculate_portfolio_var(
            positions=positions,
            correlation_matrix=stressed_book_corr,
            **var_kwargs,
        )
        stressed_standalone_vars[book_id] = standalone.var_value

    stressed_total_standalone = sum(stressed_standalone_vars.values())
    stressed_diversification_benefit = stressed_total_standalone - stressed_agg_result.var_value

    # Decompose stressed aggregate back to books
    stressed_contributions = decompose_book_contributions(
        books=books,
        aggregate_result=stressed_agg_result,
        standalone_vars=stressed_standalone_vars,
    )

    stressed_result = CrossBookVaRResult(
        var_result=stressed_agg_result,
        book_contributions=stressed_contributions,
        total_standalone_var=stressed_total_standalone,
        diversification_benefit=stressed_diversification_benefit,
    )

    base_benefit = base_result.diversification_benefit
    stressed_benefit = stressed_result.diversification_benefit
    benefit_erosion = base_benefit - stressed_benefit

    if base_benefit > 0:
        benefit_erosion_pct = (benefit_erosion / base_benefit) * 100.0
    else:
        benefit_erosion_pct = 0.0

    return StressedDiversificationResult(
        base_result=base_result,
        stressed_result=stressed_result,
        base_diversification_benefit=base_benefit,
        stressed_diversification_benefit=stressed_benefit,
        benefit_erosion=benefit_erosion,
        benefit_erosion_pct=benefit_erosion_pct,
        stress_correlation=stress_correlation,
    )
