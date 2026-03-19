from collections import defaultdict

import numpy as np

from kinetix_risk.models import (
    AssetClass,
    BookVaRContribution,
    CalculationType,
    ConfidenceLevel,
    CrossBookVaRResult,
    PositionRisk,
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

    # Decompose aggregate VaR back to book contributions
    book_contributions = decompose_book_contributions(
        books=books,
        aggregate_result=aggregate_result,
        standalone_vars=standalone_vars,
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

    # Build contribution objects
    contributions = []
    for book_id in sorted(books.keys()):
        var_contrib = book_var_contribution.get(book_id, 0.0)
        pct = (var_contrib / aggregate_var * 100.0) if aggregate_var > 0 else 0.0
        sa_var = standalone_vars.get(book_id, 0.0)
        div_benefit = sa_var - var_contrib

        contributions.append(BookVaRContribution(
            book_id=book_id,
            var_contribution=var_contrib,
            percentage_of_total=pct,
            standalone_var=sa_var,
            diversification_benefit=div_benefit,
        ))

    return contributions
