"""Tests for Brinson-Hood-Beebower performance attribution.

Covers:
  - Single-period allocation / selection / interaction effects
  - Accounting identity: allocation + selection + interaction = total active return
  - Multi-period geometric (Menchero): zero residual by construction
  - Edge cases
"""

import math
import pytest

from kinetix_risk.brinson import (
    BrinsonResult,
    SectorAttribution,
    brinson_single_period,
    brinson_multi_period,
)


# ---------------------------------------------------------------------------
# Single-period BHB tests
# ---------------------------------------------------------------------------


class TestAllocationEffect:
    @pytest.mark.unit
    def test_positive_allocation_when_overweight_outperforming_sector(self):
        # Two sectors so weights can sum to 1.0 independently per side.
        # Tech: portfolio overweight (0.60 vs 0.40), benchmark return 0.06 > total benchmark 0.04
        # → positive allocation for Tech
        result = brinson_single_period(
            sector_labels=["Tech", "Other"],
            portfolio_weights=[0.60, 0.40],
            benchmark_weights=[0.40, 0.60],
            portfolio_returns=[0.08, 0.02],
            benchmark_returns=[0.06, 0.025],
            total_benchmark_return=0.039,
        )
        tech = result.sectors[0]
        # allocation = (0.60 - 0.40) * (0.06 - 0.039) = 0.20 * 0.021 = 0.0042
        assert tech.allocation_effect > 0
        assert tech.sector_label == "Tech"

    @pytest.mark.unit
    def test_allocation_effect_formula(self):
        # Two sectors; Tech: wp=0.50, wb=0.40 → overweight by 0.10
        # Benchmark Tech return 0.06, total benchmark return 0.04
        # allocation = 0.10 * (0.06 - 0.04) = 0.002
        result = brinson_single_period(
            sector_labels=["Tech", "Other"],
            portfolio_weights=[0.50, 0.50],
            benchmark_weights=[0.40, 0.60],
            portfolio_returns=[0.08, 0.03],
            benchmark_returns=[0.06, 0.025],
            total_benchmark_return=0.04,
        )
        tech = result.sectors[0]
        assert abs(tech.allocation_effect - 0.002) < 1e-9

    @pytest.mark.unit
    def test_allocation_effect_zero_when_portfolio_weight_equals_benchmark_weight(self):
        result = brinson_single_period(
            sector_labels=["Financials", "Energy"],
            portfolio_weights=[0.30, 0.70],
            benchmark_weights=[0.30, 0.70],
            portfolio_returns=[0.05, 0.03],
            benchmark_returns=[0.04, 0.02],
            total_benchmark_return=0.032,
        )
        for sector in result.sectors:
            assert abs(sector.allocation_effect) < 1e-12, (
                f"{sector.sector_label} allocation should be zero when weights match"
            )


class TestSelectionEffect:
    @pytest.mark.unit
    def test_selection_effect_formula(self):
        # Tech: wb=0.40, r_p=0.08, r_b=0.06 → selection = 0.40 * 0.02 = 0.008
        result = brinson_single_period(
            sector_labels=["Tech", "Other"],
            portfolio_weights=[0.50, 0.50],
            benchmark_weights=[0.40, 0.60],
            portfolio_returns=[0.08, 0.03],
            benchmark_returns=[0.06, 0.025],
            total_benchmark_return=0.04,
        )
        tech = result.sectors[0]
        assert abs(tech.selection_effect - 0.008) < 1e-9

    @pytest.mark.unit
    def test_selection_effect_negative_when_underperforming(self):
        # Energy: wb=0.40, r_p=0.01 < r_b=0.05 → negative selection
        result = brinson_single_period(
            sector_labels=["Energy", "Other"],
            portfolio_weights=[0.30, 0.70],
            benchmark_weights=[0.40, 0.60],
            portfolio_returns=[0.01, 0.04],
            benchmark_returns=[0.05, 0.03],
            total_benchmark_return=0.05,
        )
        energy = result.sectors[0]
        assert energy.selection_effect < 0


class TestInteractionEffect:
    @pytest.mark.unit
    def test_interaction_effect_formula(self):
        # Tech: (w_p - w_b)=0.10, (r_p - r_b)=0.02 → interaction = 0.002
        result = brinson_single_period(
            sector_labels=["Tech", "Other"],
            portfolio_weights=[0.50, 0.50],
            benchmark_weights=[0.40, 0.60],
            portfolio_returns=[0.08, 0.03],
            benchmark_returns=[0.06, 0.025],
            total_benchmark_return=0.04,
        )
        tech = result.sectors[0]
        assert abs(tech.interaction_effect - 0.002) < 1e-9

    @pytest.mark.unit
    def test_interaction_effect_zero_when_portfolio_weight_equals_benchmark_weight(self):
        result = brinson_single_period(
            sector_labels=["Tech", "Other"],
            portfolio_weights=[0.40, 0.60],
            benchmark_weights=[0.40, 0.60],
            portfolio_returns=[0.07, 0.03],
            benchmark_returns=[0.05, 0.02],
            total_benchmark_return=0.032,
        )
        tech = result.sectors[0]
        assert abs(tech.interaction_effect) < 1e-12


class TestAccountingIdentity:
    @pytest.mark.unit
    def test_sum_of_effects_equals_total_active_return_two_sectors(self):
        """Accounting identity: sum(allocation + selection + interaction) == total active return."""
        result = brinson_single_period(
            sector_labels=["Tech", "Financials"],
            portfolio_weights=[0.60, 0.40],
            benchmark_weights=[0.50, 0.50],
            portfolio_returns=[0.08, 0.03],
            benchmark_returns=[0.07, 0.04],
            total_benchmark_return=0.055,
        )
        # total active return = portfolio return - benchmark return
        portfolio_return = sum(
            w * r for w, r in zip([0.60, 0.40], [0.08, 0.03])
        )
        expected_active = portfolio_return - 0.055

        attributed = result.total_active_return
        assert abs(attributed - expected_active) < 1e-9

    @pytest.mark.unit
    def test_accounting_identity_holds_for_five_sectors(self):
        labels = ["Tech", "Finance", "Energy", "Health", "Consumer"]
        pw = [0.25, 0.20, 0.15, 0.25, 0.15]
        bw = [0.20, 0.20, 0.20, 0.20, 0.20]
        pr = [0.10, 0.05, 0.03, 0.08, 0.06]
        br = [0.09, 0.04, 0.04, 0.07, 0.05]
        total_br = sum(w * r for w, r in zip(bw, br))

        result = brinson_single_period(
            sector_labels=labels,
            portfolio_weights=pw,
            benchmark_weights=bw,
            portfolio_returns=pr,
            benchmark_returns=br,
            total_benchmark_return=total_br,
        )
        portfolio_return = sum(w * r for w, r in zip(pw, pr))
        expected_active = portfolio_return - total_br

        assert abs(result.total_active_return - expected_active) < 1e-9


class TestTotalActiveReturnDecomposition:
    @pytest.mark.unit
    def test_total_active_return_equals_sum_of_sector_totals(self):
        result = brinson_single_period(
            sector_labels=["A", "B"],
            portfolio_weights=[0.40, 0.60],
            benchmark_weights=[0.50, 0.50],
            portfolio_returns=[0.06, 0.04],
            benchmark_returns=[0.05, 0.03],
            total_benchmark_return=0.04,
        )
        sector_total = sum(
            s.allocation_effect + s.selection_effect + s.interaction_effect
            for s in result.sectors
        )
        assert abs(result.total_active_return - sector_total) < 1e-12


# ---------------------------------------------------------------------------
# Multi-period geometric (Menchero) tests
# ---------------------------------------------------------------------------


class TestMultiPeriodGeometric:
    @pytest.mark.unit
    def test_two_period_geometric_produces_zero_residual(self):
        """Multi-period geometric linking must produce zero residual by construction."""
        # Two periods, two sectors each
        periods = [
            dict(
                sector_labels=["Tech", "Finance"],
                portfolio_weights=[0.60, 0.40],
                benchmark_weights=[0.50, 0.50],
                portfolio_returns=[0.05, 0.02],
                benchmark_returns=[0.04, 0.03],
                total_benchmark_return=0.035,
            ),
            dict(
                sector_labels=["Tech", "Finance"],
                portfolio_weights=[0.55, 0.45],
                benchmark_weights=[0.50, 0.50],
                portfolio_returns=[0.03, 0.04],
                benchmark_returns=[0.02, 0.03],
                total_benchmark_return=0.025,
            ),
        ]
        result = brinson_multi_period(periods)

        # Residual = total active return - (allocation + selection + interaction)
        total_attributed = (
            result.total_allocation_effect
            + result.total_selection_effect
            + result.total_interaction_effect
        )
        residual = abs(result.total_active_return - total_attributed)
        assert residual < 1e-9, f"Geometric residual should be zero, got {residual}"

    @pytest.mark.unit
    def test_multi_period_total_active_return_is_geometric_compounding(self):
        """Total active return across periods equals (1+R_p)/(1+R_b) - 1."""
        pw1, bw1 = [0.60, 0.40], [0.50, 0.50]
        pr1, br1 = [0.05, 0.02], [0.04, 0.03]
        pw2, bw2 = [0.55, 0.45], [0.50, 0.50]
        pr2, br2 = [0.03, 0.04], [0.02, 0.03]

        total_br1 = sum(w * r for w, r in zip(bw1, br1))
        total_br2 = sum(w * r for w, r in zip(bw2, br2))
        total_pr1 = sum(w * r for w, r in zip(pw1, pr1))
        total_pr2 = sum(w * r for w, r in zip(pw2, pr2))

        cumulative_portfolio = (1 + total_pr1) * (1 + total_pr2) - 1
        cumulative_benchmark = (1 + total_br1) * (1 + total_br2) - 1
        expected_active = (1 + cumulative_portfolio) / (1 + cumulative_benchmark) - 1

        periods = [
            dict(
                sector_labels=["Tech", "Finance"],
                portfolio_weights=pw1,
                benchmark_weights=bw1,
                portfolio_returns=pr1,
                benchmark_returns=br1,
                total_benchmark_return=total_br1,
            ),
            dict(
                sector_labels=["Tech", "Finance"],
                portfolio_weights=pw2,
                benchmark_weights=bw2,
                portfolio_returns=pr2,
                benchmark_returns=br2,
                total_benchmark_return=total_br2,
            ),
        ]
        result = brinson_multi_period(periods)
        assert abs(result.total_active_return - expected_active) < 1e-9

    @pytest.mark.unit
    def test_single_period_multi_period_effects_are_proportionally_consistent(self):
        """For a single period, multi-period geometric and single-period arithmetic
        attribution effects should be proportional to the same ratio.

        Specifically: allocation / selection ratio must be identical, because Menchero
        scaling preserves the relative proportions of each effect within the period.
        """
        kwargs = dict(
            sector_labels=["A", "B"],
            portfolio_weights=[0.40, 0.60],
            benchmark_weights=[0.50, 0.50],
            portfolio_returns=[0.06, 0.04],
            benchmark_returns=[0.05, 0.03],
            total_benchmark_return=0.04,
        )
        single = brinson_single_period(**kwargs)
        multi = brinson_multi_period([kwargs])

        # Both results must satisfy the zero-residual property independently
        single_attributed = (
            single.total_allocation_effect
            + single.total_selection_effect
            + single.total_interaction_effect
        )
        assert abs(single.total_active_return - single_attributed) < 1e-9

        multi_attributed = (
            multi.total_allocation_effect
            + multi.total_selection_effect
            + multi.total_interaction_effect
        )
        assert abs(multi.total_active_return - multi_attributed) < 1e-9

        # The relative split between allocation and selection must be preserved
        if abs(single.total_allocation_effect) > 1e-12:
            ratio_single = single.total_selection_effect / single.total_allocation_effect
            ratio_multi = multi.total_selection_effect / multi.total_allocation_effect
            assert abs(ratio_single - ratio_multi) < 1e-9


# ---------------------------------------------------------------------------
# Input validation
# ---------------------------------------------------------------------------


class TestInputValidation:
    @pytest.mark.unit
    def test_raises_when_weights_do_not_sum_to_one(self):
        with pytest.raises(ValueError, match="portfolio weights"):
            brinson_single_period(
                sector_labels=["A"],
                portfolio_weights=[0.50],  # should sum to 1.0
                benchmark_weights=[1.00],
                portfolio_returns=[0.05],
                benchmark_returns=[0.04],
                total_benchmark_return=0.04,
            )

    @pytest.mark.unit
    def test_raises_when_mismatched_sector_count(self):
        with pytest.raises(ValueError):
            brinson_single_period(
                sector_labels=["A", "B"],
                portfolio_weights=[0.50, 0.50],
                benchmark_weights=[1.00],  # wrong length
                portfolio_returns=[0.05, 0.04],
                benchmark_returns=[0.04],
                total_benchmark_return=0.04,
            )

    @pytest.mark.unit
    def test_handles_single_sector(self):
        result = brinson_single_period(
            sector_labels=["All"],
            portfolio_weights=[1.0],
            benchmark_weights=[1.0],
            portfolio_returns=[0.07],
            benchmark_returns=[0.05],
            total_benchmark_return=0.05,
        )
        assert len(result.sectors) == 1
        # With identical weights: allocation = 0, interaction = 0, selection = 1.0*0.02 = 0.02
        assert abs(result.sectors[0].allocation_effect) < 1e-12
        assert abs(result.sectors[0].interaction_effect) < 1e-12
        assert abs(result.sectors[0].selection_effect - 0.02) < 1e-9
