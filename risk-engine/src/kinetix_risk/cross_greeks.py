"""Analytical cross-Greeks using Black-Scholes formulas.

Cross-Greeks are second-order sensitivities that capture how one Greek
changes with respect to another market variable:

- Vanna: d(delta)/d(vol) = d(vega)/d(S)
- Volga (Vomma): d(vega)/d(vol) = d^2(price)/d(vol)^2
- Charm (delta decay): -d(delta)/d(T) = rate of change of delta over time
"""

import math

from scipy.stats import norm

from kinetix_risk.models import OptionType


def _d1(S: float, K: float, T: float, r: float, sigma: float, q: float = 0.0) -> float:
    if T <= 0:
        return 0.0
    return (math.log(S / K) + (r - q + 0.5 * sigma**2) * T) / (sigma * math.sqrt(T))


def _d2(S: float, K: float, T: float, r: float, sigma: float, q: float = 0.0) -> float:
    if T <= 0:
        return 0.0
    return _d1(S, K, T, r, sigma, q) - sigma * math.sqrt(T)


def calculate_vanna(S: float, K: float, T: float, r: float, sigma: float, q: float = 0.0) -> float:
    """Calculate Vanna: d(delta)/d(vol) = d(vega)/d(S).

    Vanna = -e^{-d1^2/2} * d2 / (sigma * sqrt(2*pi*T))
          = vega/S * (1 - d1/(sigma*sqrt(T)))

    Equivalently: vega * d2 / (S * sigma * sqrt(T))
    but the sign-stable form is: (sqrt(T) * norm.pdf(d1) * d2) / sigma
    which simplifies to: -norm.pdf(d1) * d2 / sigma   (per unit spot).

    Using the standard form: vanna = (norm.pdf(d1) / sigma) * (1 - d1 / (sigma * sqrt(T)))
    but the cleanest analytical form is:
        vanna = -(norm.pdf(d1) * d2) / sigma
    """
    if T <= 0:
        return 0.0
    d1 = _d1(S, K, T, r, sigma, q)
    d2 = _d2(S, K, T, r, sigma, q)
    sqrt_t = math.sqrt(T)
    # Vanna = (vega / S) * (1 - d1/(sigma*sqrt(T)))
    # vega = S * norm.pdf(d1) * sqrt(T)
    # So vanna = norm.pdf(d1) * sqrt(T) * (1 - d1/(sigma*sqrt(T)))
    #          = norm.pdf(d1) * (sqrt(T) - d1/sigma)
    # Equivalently: -norm.pdf(d1) * d2 / sigma  (using d2 = d1 - sigma*sqrt(T))
    return float(-norm.pdf(d1) * d2 / sigma)


def calculate_volga(S: float, K: float, T: float, r: float, sigma: float, q: float = 0.0) -> float:
    """Calculate Volga (Vomma): d(vega)/d(vol) = d^2(price)/d(vol)^2.

    Volga = vega * d1 * d2 / sigma
          = S * sqrt(T) * norm.pdf(d1) * d1 * d2 / sigma

    Volga is non-negative away from ATM and zero at ATM where d1*d2 crosses zero.
    """
    if T <= 0:
        return 0.0
    d1 = _d1(S, K, T, r, sigma, q)
    d2 = _d2(S, K, T, r, sigma, q)
    sqrt_t = math.sqrt(T)
    vega = S * float(norm.pdf(d1)) * sqrt_t
    return float(vega * d1 * d2 / sigma)


def calculate_charm(
    S: float,
    K: float,
    T: float,
    r: float,
    sigma: float,
    option_type: OptionType = OptionType.CALL,
    q: float = 0.0,
) -> float:
    """Calculate Charm: -d(delta)/d(T), the rate of delta decay.

    For a call:
        charm = -norm.pdf(d1) * (2*r*T - d2*sigma*sqrt(T)) / (2*T*sigma*sqrt(T))

    For a put, charm_put = charm_call + r*exp(-r*T)  (from put-call parity).
    """
    if T <= 0:
        return 0.0
    d1 = _d1(S, K, T, r, sigma, q)
    d2 = _d2(S, K, T, r, sigma, q)
    sqrt_t = math.sqrt(T)
    pdf_d1 = float(norm.pdf(d1))

    charm_call = -pdf_d1 * (2 * (r - q) * T - d2 * sigma * sqrt_t) / (2 * T * sigma * sqrt_t)

    if option_type == OptionType.CALL:
        return float(charm_call)
    else:
        return float(charm_call + q * math.exp(-q * T))
