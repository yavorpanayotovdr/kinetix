"""Credit exposure calculations: Potential Future Exposure (PFE) and
Credit Valuation Adjustment (CVA).

PFE is computed via Monte Carlo simulation of future mark-to-market paths
for a netting set.  CVA uses the discrete approximation:

    CVA = sum_t  PD(t) * LGD * EPE(t) * DF(t)

where EPE(t) is the Expected Positive Exposure at tenor t derived from the
PFE simulation.

Design decisions:
  - PFE reuses the Cholesky-based MC infrastructure from var_monte_carlo.py
    rather than duplicating the normal random draw logic.
  - Deterministic PD for v1: hazard rate from CDS spread or rating table.
  - No CDS data: sector-average spread, result flagged ESTIMATED.
  - Wrong-way risk: heuristic sector match between counterparty and position.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

# Tenor labels and their year fractions
TENORS: list[tuple[str, float]] = [
    ("1M", 1 / 12),
    ("3M", 3 / 12),
    ("6M", 6 / 12),
    ("1Y", 1.0),
    ("2Y", 2.0),
    ("5Y", 5.0),
]

# Basel II / CRD IV rating-to-annual-PD mapping (midpoints of each grade band)
_RATING_PD: dict[str, float] = {
    "AAA": 0.0001,
    "AA+": 0.0002,
    "AA": 0.0002,
    "AA-": 0.0003,
    "A+": 0.0005,
    "A": 0.0008,
    "A-": 0.0010,
    "BBB+": 0.0015,
    "BBB": 0.0020,
    "BBB-": 0.0030,
    "BB+": 0.0050,
    "BB": 0.0100,
    "BB-": 0.0170,
    "B+": 0.0300,
    "B": 0.0500,
    "B-": 0.0800,
    "CCC": 0.1500,
    "CC": 0.2500,
    "C": 0.4000,
    "D": 1.0000,
    "UNRATED": 0.0050,  # treat as BB+ equivalent
}

# Sector-average CDS spreads (bps) used when no CDS data is available.
_SECTOR_AVERAGE_CDS_BPS: dict[str, float] = {
    "FINANCIALS": 80.0,
    "ENERGY": 120.0,
    "TECHNOLOGY": 50.0,
    "HEALTHCARE": 60.0,
    "INDUSTRIALS": 90.0,
    "CONSUMER_DISCRETIONARY": 100.0,
    "CONSUMER_STAPLES": 55.0,
    "UTILITIES": 70.0,
    "MATERIALS": 110.0,
    "REAL_ESTATE": 130.0,
    "COMMUNICATION_SERVICES": 75.0,
    "GOVERNMENT": 10.0,
    "OTHER": 100.0,
}

# Default risk-free discount factor approximation (flat 5% curve)
_DEFAULT_RISK_FREE_RATE = 0.05

# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class PositionExposure:
    """Input: a single position within a netting set."""

    instrument_id: str
    market_value: float          # current MtM in base currency
    asset_class: str             # e.g. "EQUITY", "FIXED_INCOME"
    volatility: float            # annualised vol for MC simulation
    sector: str = "OTHER"        # for wrong-way risk detection


@dataclass(frozen=True)
class ExposureProfile:
    """Output: exposure statistics at a single tenor."""

    tenor: str
    tenor_years: float
    expected_exposure: float     # mean of positive exposures (EPE)
    pfe_95: float                # 95th-percentile peak exposure
    pfe_99: float                # 99th-percentile peak exposure


@dataclass(frozen=True)
class NettingSetExposure:
    """Exposure summary for one netting agreement."""

    netting_set_id: str
    agreement_type: str
    gross_exposure: float
    net_exposure: float
    netting_benefit: float
    netting_benefit_pct: float
    position_count: int


@dataclass(frozen=True)
class WrongWayRiskFlag:
    """Heuristic wrong-way risk detection result for one position."""

    instrument_id: str
    counterparty_sector: str
    position_sector: str
    exposure: float
    message: str


@dataclass(frozen=True)
class PFEResult:
    """Output of calculate_pfe()."""

    counterparty_id: str
    exposure_profile: list[ExposureProfile]
    netting_set_id: str
    gross_exposure: float
    net_exposure: float


@dataclass(frozen=True)
class CVAResult:
    """Output of calculate_cva()."""

    counterparty_id: str
    cva: float
    is_estimated: bool           # True when CDS data unavailable
    hazard_rate: float
    pd_1y: float


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------


def _nearest_positive_definite(matrix: np.ndarray) -> np.ndarray:
    """Clip negative eigenvalues to a small positive value."""
    eigenvalues, eigenvectors = np.linalg.eigh(matrix)
    eigenvalues = np.maximum(eigenvalues, 1e-10)
    return eigenvectors @ np.diag(eigenvalues) @ eigenvectors.T


def _rating_to_pd(rating: str | None) -> float:
    """Return annual PD from S&P rating string.  Defaults to BB+ if unknown."""
    if rating is None:
        return _RATING_PD["UNRATED"]
    return _RATING_PD.get(rating.upper().strip(), _RATING_PD["UNRATED"])


def _cds_to_hazard_rate(cds_spread_bps: float, lgd: float) -> float:
    """Approximate hazard rate: lambda ≈ CDS_spread / LGD.

    Uses the constant-intensity reduced-form relationship under flat hazard
    assumption.  LGD must be positive (default 0.40).
    """
    if lgd <= 0.0:
        raise ValueError("LGD must be positive")
    return (cds_spread_bps / 10_000.0) / lgd


def _pd_from_hazard(hazard_rate: float, t_years: float) -> float:
    """Marginal (survival-adjusted) PD in period ending at t_years.

    P(default in (t-dt, t]) = exp(-h*(t-dt)) - exp(-h*t)
    For discrete tenors we use the difference of survival probabilities.
    """
    if t_years <= 0.0:
        return 0.0
    return 1.0 - math.exp(-hazard_rate * t_years)


def _discount_factor(rate: float, t_years: float) -> float:
    """Continuous discounting: DF(t) = exp(-r*t)."""
    return math.exp(-rate * t_years)


# ---------------------------------------------------------------------------
# PFE calculation
# ---------------------------------------------------------------------------


def calculate_pfe(
    counterparty_id: str,
    netting_set_id: str,
    agreement_type: str,
    positions: list[PositionExposure],
    num_simulations: int = 10_000,
    seed: int | None = 42,
    tenors: list[tuple[str, float]] | None = None,
    correlation_matrix: np.ndarray | None = None,
    pfe_confidence: float = 0.95,
) -> PFEResult:
    """Simulate PFE profiles via Monte Carlo for a single netting set.

    For each tenor:
      1. Simulate correlated GBM paths for all positions in the netting set.
      2. At that tenor compute the netting-set MtM for each simulation path.
      3. Exposure = max(0, MtM) — we have credit risk only when we're owed money.
      4. EPE = mean of positive exposures; PFE_95 = 95th percentile.

    Parameters
    ----------
    positions:
        Positions belonging to this netting set.  If empty, all metrics = 0.
    correlation_matrix:
        If None, an identity matrix is used (zero inter-position correlation).
    pfe_confidence:
        Percentile for PFE output (default 0.95).
    """
    if tenors is None:
        tenors = TENORS

    if not positions:
        empty_profiles = [
            ExposureProfile(label, t, 0.0, 0.0, 0.0) for label, t in tenors
        ]
        return PFEResult(
            counterparty_id=counterparty_id,
            exposure_profile=empty_profiles,
            netting_set_id=netting_set_id,
            gross_exposure=0.0,
            net_exposure=0.0,
        )

    n = len(positions)
    rng = np.random.default_rng(seed)

    market_values = np.array([p.market_value for p in positions])
    annual_vols = np.array([p.volatility for p in positions])

    if correlation_matrix is None:
        corr = np.eye(n)
    else:
        corr = correlation_matrix

    # Ensure positive-definite
    try:
        chol = np.linalg.cholesky(corr)
    except np.linalg.LinAlgError:
        repaired = _nearest_positive_definite(corr)
        try:
            chol = np.linalg.cholesky(repaired)
        except np.linalg.LinAlgError:
            raise ValueError(
                "Correlation matrix is not positive-definite and could not be repaired"
            )

    # Pre-generate correlated standard normals once — shape (num_sim, n_positions)
    z = rng.standard_normal((num_simulations, n))
    # Correlated shocks (same for all tenors; each tenor re-scales time)
    correlated_z = z @ chol.T  # (num_sim, n)

    gross_exposure = float(np.sum(np.abs(market_values)))
    net_exposure = max(0.0, float(np.sum(market_values)))

    exposure_profiles: list[ExposureProfile] = []

    for label, t in tenors:
        # Under GBM: S(t) = S(0) * exp((mu - 0.5*sigma^2)*t + sigma*sqrt(t)*Z)
        # We assume drift mu=0 (risk-neutral for exposure simulation)
        drift = -0.5 * annual_vols**2 * t           # (n,)
        diffusion = annual_vols * math.sqrt(t)       # (n,)

        # Simulated market values at tenor t: shape (num_sim, n)
        log_returns = drift + diffusion * correlated_z  # broadcast correctly
        sim_values = market_values * np.exp(log_returns)

        # Netting-set MtM at tenor t for each simulation
        netting_set_mtm = sim_values.sum(axis=1)  # (num_sim,)

        # Exposure = max(0, MtM)
        positive_exposure = np.maximum(0.0, netting_set_mtm)

        epe = float(np.mean(positive_exposure))
        pfe_95 = float(np.percentile(positive_exposure, pfe_confidence * 100))
        pfe_99 = float(np.percentile(positive_exposure, 99.0))

        exposure_profiles.append(
            ExposureProfile(
                tenor=label,
                tenor_years=t,
                expected_exposure=epe,
                pfe_95=pfe_95,
                pfe_99=pfe_99,
            )
        )

    return PFEResult(
        counterparty_id=counterparty_id,
        exposure_profile=exposure_profiles,
        netting_set_id=netting_set_id,
        gross_exposure=gross_exposure,
        net_exposure=net_exposure,
    )


# ---------------------------------------------------------------------------
# CVA calculation
# ---------------------------------------------------------------------------


def calculate_cva(
    counterparty_id: str,
    exposure_profile: list[ExposureProfile],
    lgd: float,
    pd_1y: float | None = None,
    cds_spread_bps: float | None = None,
    rating: str | None = None,
    sector: str | None = None,
    risk_free_rate: float = _DEFAULT_RISK_FREE_RATE,
) -> CVAResult:
    """Calculate CVA using discrete tenor approximation.

    CVA = sum_t  PD(t) * LGD * EPE(t) * DF(t)

    Where PD(t) is the marginal default probability in period (t-1, t].
    Hazard rate is derived (in priority order):
      1. From explicit pd_1y if provided: h = -ln(1 - pd_1y)
      2. From cds_spread_bps: h ≈ cds_spread / LGD
      3. From rating table (deterministic Basel II mapping)
      4. From sector-average CDS spread — result flagged ESTIMATED

    Parameters
    ----------
    exposure_profile:
        EPE at each tenor from calculate_pfe().  Empty list → CVA = 0.
    lgd:
        Loss Given Default (0.40 = 40%).  Must be in (0, 1].
    """
    if not exposure_profile:
        return CVAResult(
            counterparty_id=counterparty_id,
            cva=0.0,
            is_estimated=False,
            hazard_rate=0.0,
            pd_1y=0.0,
        )

    if lgd <= 0.0 or lgd > 1.0:
        raise ValueError(f"LGD must be in (0, 1], got {lgd}")

    is_estimated = False

    # Determine hazard rate
    if pd_1y is not None and pd_1y > 0.0:
        # Back out flat hazard rate from 1-year PD
        hazard_rate = -math.log(1.0 - min(pd_1y, 0.9999))
    elif cds_spread_bps is not None and cds_spread_bps > 0.0:
        hazard_rate = _cds_to_hazard_rate(cds_spread_bps, lgd)
    elif rating is not None:
        annual_pd = _rating_to_pd(rating)
        hazard_rate = -math.log(1.0 - min(annual_pd, 0.9999))
    else:
        # Fall back to sector-average spread — ESTIMATED
        effective_sector = (sector or "OTHER").upper()
        avg_spread = _SECTOR_AVERAGE_CDS_BPS.get(effective_sector, _SECTOR_AVERAGE_CDS_BPS["OTHER"])
        hazard_rate = _cds_to_hazard_rate(avg_spread, lgd)
        is_estimated = True

    implied_pd_1y = 1.0 - math.exp(-hazard_rate * 1.0)

    # Discrete CVA sum over tenors
    cva_sum = 0.0
    prev_t = 0.0

    for ep in exposure_profile:
        t = ep.tenor_years
        if t <= prev_t:
            continue

        # Marginal default probability in (prev_t, t]
        surv_start = math.exp(-hazard_rate * prev_t)
        surv_end = math.exp(-hazard_rate * t)
        marginal_pd = surv_start - surv_end

        df = _discount_factor(risk_free_rate, t)
        cva_sum += marginal_pd * lgd * ep.expected_exposure * df

        prev_t = t

    return CVAResult(
        counterparty_id=counterparty_id,
        cva=cva_sum,
        is_estimated=is_estimated,
        hazard_rate=hazard_rate,
        pd_1y=implied_pd_1y,
    )


# ---------------------------------------------------------------------------
# Wrong-way risk detection
# ---------------------------------------------------------------------------


def detect_wrong_way_risk(
    counterparty_sector: str,
    positions: list[PositionExposure],
) -> list[WrongWayRiskFlag]:
    """Heuristic: flag positions whose sector matches the counterparty sector.

    Wrong-way risk arises when counterparty credit quality is correlated with
    our exposure to them.  E.g. if our counterparty is a bank and we hold
    financial-sector equities, both deteriorate together.
    """
    flags: list[WrongWayRiskFlag] = []
    cp_sector = counterparty_sector.upper()

    for pos in positions:
        pos_sector = pos.sector.upper()
        if pos_sector == cp_sector and abs(pos.market_value) > 0.0:
            flags.append(
                WrongWayRiskFlag(
                    instrument_id=pos.instrument_id,
                    counterparty_sector=counterparty_sector,
                    position_sector=pos.sector,
                    exposure=abs(pos.market_value),
                    message=(
                        f"Wrong-way risk: counterparty sector '{counterparty_sector}' "
                        f"matches position sector '{pos.sector}' for {pos.instrument_id}"
                    ),
                )
            )

    return flags
