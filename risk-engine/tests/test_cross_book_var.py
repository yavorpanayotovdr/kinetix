import pytest

from kinetix_risk.cross_book_var import calculate_cross_book_var, decompose_book_contributions
from kinetix_risk.models import (
    AssetClass,
    BookVaRContribution,
    CalculationType,
    ConfidenceLevel,
    CrossBookVaRResult,
    PositionRisk,
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
