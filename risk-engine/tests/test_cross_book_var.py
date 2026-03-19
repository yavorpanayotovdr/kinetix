import pytest

from kinetix_risk.cross_book_var import (
    calculate_cross_book_var,
    calculate_stressed_cross_book_var,
    decompose_book_contributions,
)
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


def make_position(instrument_id: str, asset_class: AssetClass, market_value: float) -> PositionRisk:
    return PositionRisk(instrument_id, asset_class, market_value, "USD")


@pytest.mark.unit
class TestCrossBookVaR:

    def test_two_books_different_asset_classes_produces_diversification_benefit(self):
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 100_000.0)],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        assert isinstance(result, CrossBookVaRResult)
        assert result.diversification_benefit > 0
        # Diversified VaR should be less than sum of standalone VaRs
        assert result.var_result.var_value < result.total_standalone_var

    def test_single_book_matches_individual_var(self):
        from kinetix_risk.portfolio_risk import calculate_portfolio_var

        positions = [
            make_position("AAPL", AssetClass.EQUITY, 100_000.0),
            make_position("MSFT", AssetClass.EQUITY, 50_000.0),
        ]
        books = {"book-A": positions}

        cross_result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        standalone_result = calculate_portfolio_var(
            positions,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )

        assert cross_result.var_result.var_value == pytest.approx(
            standalone_result.var_value, rel=1e-6
        )
        assert len(cross_result.book_contributions) == 1
        assert cross_result.book_contributions[0].book_id == "book-A"
        assert cross_result.diversification_benefit == pytest.approx(0.0, abs=1e-10)

    def test_book_contributions_sum_to_aggregate_var(self):
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 200_000.0)],
            "book-C": [make_position("EURUSD", AssetClass.FX, 50_000.0)],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        contribution_sum = sum(c.var_contribution for c in result.book_contributions)
        assert contribution_sum == pytest.approx(result.var_result.var_value, rel=1e-6)

    def test_hedging_positions_across_books(self):
        # Book A long equity, Book B short equity — should partially offset
        books_hedged = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("SPY-SHORT", AssetClass.EQUITY, -80_000.0)],
        }
        books_unhedged = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
        }
        result_hedged = calculate_cross_book_var(
            books_hedged,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        result_unhedged = calculate_cross_book_var(
            books_unhedged,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        # Hedged portfolio should have lower VaR than unhedged
        assert result_hedged.var_result.var_value < result_unhedged.var_result.var_value

    def test_empty_book_excluded_from_calculation(self):
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        assert result.var_result.var_value > 0
        # Empty book should still appear in contributions but with zero contribution
        book_ids = [c.book_id for c in result.book_contributions]
        assert "book-A" in book_ids
        # book-B may be excluded or have zero contribution
        book_b = [c for c in result.book_contributions if c.book_id == "book-B"]
        if book_b:
            assert book_b[0].var_contribution == pytest.approx(0.0, abs=1e-10)
            assert book_b[0].standalone_var == pytest.approx(0.0, abs=1e-10)

    def test_all_empty_books_raises_error(self):
        books = {
            "book-A": [],
            "book-B": [],
        }
        with pytest.raises(ValueError, match="[Ee]mpty|no positions"):
            calculate_cross_book_var(
                books,
                calculation_type=CalculationType.PARAMETRIC,
                confidence_level=ConfidenceLevel.CL_95,
                time_horizon_days=1,
            )

    def test_cl_975_var_is_between_cl_95_and_cl_99(self):
        from kinetix_risk.portfolio_risk import calculate_portfolio_var

        positions = [
            make_position("AAPL", AssetClass.EQUITY, 100_000.0),
            make_position("UST10Y", AssetClass.FIXED_INCOME, 200_000.0),
        ]

        var_95 = calculate_portfolio_var(
            positions,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        var_975 = calculate_portfolio_var(
            positions,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_975,
            time_horizon_days=1,
        )
        var_99 = calculate_portfolio_var(
            positions,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_99,
            time_horizon_days=1,
        )

        assert var_975.var_value > 0
        assert var_95.var_value < var_975.var_value
        assert var_975.var_value < var_99.var_value

    def test_cross_book_var_with_cl_975(self):
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 100_000.0)],
        }

        result_95 = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        result_975 = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_975,
            time_horizon_days=1,
        )
        result_99 = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_99,
            time_horizon_days=1,
        )

        assert isinstance(result_975, CrossBookVaRResult)
        assert result_975.var_result.var_value > 0
        assert result_95.var_result.var_value < result_975.var_result.var_value
        assert result_975.var_result.var_value < result_99.var_result.var_value

    def test_marginal_var_is_positive_for_non_empty_books(self):
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 200_000.0)],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        for c in result.book_contributions:
            if c.var_contribution > 0:
                assert c.marginal_var > 0, f"Book {c.book_id} has positive contribution but zero marginal VaR"

    def test_marginal_var_is_zero_for_empty_books(self):
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        book_b = [c for c in result.book_contributions if c.book_id == "book-B"]
        if book_b:
            assert book_b[0].marginal_var == pytest.approx(0.0, abs=1e-10)

    def test_incremental_var_computed_for_all_non_empty_books(self):
        """For 3 books, each non-empty book should have a non-zero incremental VaR,
        and removing a diversifying book should yield a positive incremental VaR
        (portfolio VaR decreases without it)."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 200_000.0)],
            "book-C": [make_position("EURUSD", AssetClass.FX, 50_000.0)],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        # All three non-empty books should have non-zero incremental VaR
        for c in result.book_contributions:
            assert c.incremental_var != 0.0, f"Book {c.book_id} has zero incremental VaR"
        # Each book adds risk to the portfolio, so removing any one reduces VaR
        # (incremental VaR > 0 means portfolio VaR drops when that book is removed)
        for c in result.book_contributions:
            assert c.incremental_var > 0, (
                f"Book {c.book_id}: expected positive incremental VaR for a risk-adding book"
            )

    def test_incremental_var_for_single_book_equals_aggregate_var(self):
        """With only one book, incremental VaR = aggregate VaR (removing it removes everything)."""
        books = {
            "book-A": [
                make_position("AAPL", AssetClass.EQUITY, 100_000.0),
                make_position("MSFT", AssetClass.EQUITY, 50_000.0),
            ],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        assert len(result.book_contributions) == 1
        assert result.book_contributions[0].incremental_var == pytest.approx(
            result.var_result.var_value, rel=1e-6
        )

    def test_incremental_var_is_positive_for_contributing_books(self):
        """Books with positive VaR contribution should have positive incremental VaR."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 200_000.0)],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        for c in result.book_contributions:
            if c.var_contribution > 0:
                assert c.incremental_var > 0, (
                    f"Book {c.book_id} has positive contribution but non-positive incremental VaR"
                )

    def test_incremental_var_for_hedging_book_is_negative(self):
        """A book that hedges (reduces portfolio VaR) should have negative incremental VaR —
        removing it INCREASES VaR."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("SPY-SHORT", AssetClass.EQUITY, -90_000.0)],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        # Book B is a hedge — it offsets book A's equity exposure.
        # Removing book B should INCREASE VaR, so incremental VaR for B is negative.
        book_b = [c for c in result.book_contributions if c.book_id == "book-B"][0]
        assert book_b.incremental_var < 0, (
            "Hedging book should have negative incremental VaR (removing it increases portfolio VaR)"
        )

    def test_percentage_of_total_sums_to_100(self):
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 200_000.0)],
            "book-C": [
                make_position("EURUSD", AssetClass.FX, 50_000.0),
                make_position("GOLD", AssetClass.COMMODITY, 75_000.0),
            ],
        }
        result = calculate_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
        )
        pct_sum = sum(c.percentage_of_total for c in result.book_contributions)
        assert pct_sum == pytest.approx(100.0, abs=0.01)


@pytest.mark.unit
class TestStressedCrossBookVaR:

    def test_stressed_var_exceeds_base_var(self):
        """With correlation spike to 0.9, stressed VaR should be >= base VaR."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 100_000.0)],
        }
        result = calculate_stressed_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            stress_correlation=0.9,
        )
        assert isinstance(result, StressedDiversificationResult)
        assert result.stressed_result.var_result.var_value >= result.base_result.var_result.var_value

    def test_stressed_diversification_benefit_is_lower(self):
        """Stressed diversification benefit should be <= base diversification benefit."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 100_000.0)],
        }
        result = calculate_stressed_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            stress_correlation=0.9,
        )
        assert result.stressed_diversification_benefit <= result.base_diversification_benefit

    def test_benefit_erosion_is_positive(self):
        """benefit_erosion should be >= 0."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 100_000.0)],
        }
        result = calculate_stressed_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            stress_correlation=0.9,
        )
        assert result.benefit_erosion >= 0

    def test_perfect_correlation_eliminates_diversification(self):
        """With stress_correlation=1.0, diversification benefit should be ~0."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 100_000.0)],
        }
        result = calculate_stressed_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            stress_correlation=1.0,
        )
        assert result.stressed_diversification_benefit == pytest.approx(0.0, abs=1.0)
        assert result.benefit_erosion_pct == pytest.approx(100.0, abs=5.0)

    def test_stress_correlation_field_stored_on_result(self):
        """The stress_correlation parameter should be stored on the result."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 100_000.0)],
        }
        result = calculate_stressed_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            stress_correlation=0.85,
        )
        assert result.stress_correlation == 0.85

    def test_stressed_with_multiple_asset_classes(self):
        """Stressed VaR should work across multiple asset classes."""
        books = {
            "book-A": [make_position("AAPL", AssetClass.EQUITY, 100_000.0)],
            "book-B": [make_position("UST10Y", AssetClass.FIXED_INCOME, 200_000.0)],
            "book-C": [make_position("EURUSD", AssetClass.FX, 50_000.0)],
        }
        result = calculate_stressed_cross_book_var(
            books,
            calculation_type=CalculationType.PARAMETRIC,
            confidence_level=ConfidenceLevel.CL_95,
            time_horizon_days=1,
            stress_correlation=0.9,
        )
        assert result.stressed_result.var_result.var_value > 0
        assert result.benefit_erosion >= 0
