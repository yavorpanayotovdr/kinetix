from enum import Enum


class CreditRating(str, Enum):
    AAA = "AAA"
    AA_PLUS = "AA+"
    AA = "AA"
    AA_MINUS = "AA-"
    A_PLUS = "A+"
    A = "A"
    A_MINUS = "A-"
    BBB_PLUS = "BBB+"
    BBB = "BBB"
    BBB_MINUS = "BBB-"
    BB_PLUS = "BB+"
    BB = "BB"
    BB_MINUS = "BB-"
    B_PLUS = "B+"
    B = "B"
    B_MINUS = "B-"
    CCC = "CCC"
    CC = "CC"
    C = "C"
    D = "D"
    UNRATED = "UNRATED"


class Seniority(str, Enum):
    SENIOR_SECURED = "SENIOR_SECURED"
    SENIOR_UNSECURED = "SENIOR_UNSECURED"
    SUBORDINATED = "SUBORDINATED"
    EQUITY = "EQUITY"


# Default probability mapping (BCBS standard CRR3 calibration)
CREDIT_QUALITY_DEFAULT_PROBS: dict[CreditRating, float] = {
    CreditRating.AAA: 0.0003,
    CreditRating.AA_PLUS: 0.0005,
    CreditRating.AA: 0.0007,
    CreditRating.AA_MINUS: 0.001,
    CreditRating.A_PLUS: 0.0015,
    CreditRating.A: 0.002,
    CreditRating.A_MINUS: 0.003,
    CreditRating.BBB_PLUS: 0.005,
    CreditRating.BBB: 0.007,
    CreditRating.BBB_MINUS: 0.01,
    CreditRating.BB_PLUS: 0.015,
    CreditRating.BB: 0.02,
    CreditRating.BB_MINUS: 0.03,
    CreditRating.B_PLUS: 0.04,
    CreditRating.B: 0.06,
    CreditRating.B_MINUS: 0.10,
    CreditRating.CCC: 0.15,
    CreditRating.CC: 0.30,
    CreditRating.C: 0.50,
    CreditRating.D: 1.0,
    CreditRating.UNRATED: 0.015,
}

# LGD by seniority
SENIORITY_LGD: dict[Seniority, float] = {
    Seniority.SENIOR_SECURED: 0.25,
    Seniority.SENIOR_UNSECURED: 0.45,
    Seniority.SUBORDINATED: 0.75,
    Seniority.EQUITY: 1.0,
}


def maturity_weight(maturity_years: float) -> float:
    """CRR3 maturity weight: capped at max(0.25, min(maturity / 5, 1.0))."""
    return max(0.25, min(maturity_years / 5.0, 1.0))
