import math

from scipy.stats import norm

from kinetix_risk.models import OptionPosition, OptionType


def _d1(option: OptionPosition) -> float:
    S = option.spot_price
    K = option.strike
    r = option.risk_free_rate
    T = option.expiry_days / 365.0
    vol = option.implied_vol
    return (math.log(S / K) + (r + 0.5 * vol ** 2) * T) / (vol * math.sqrt(T))


def _d2(option: OptionPosition) -> float:
    T = option.expiry_days / 365.0
    return _d1(option) - option.implied_vol * math.sqrt(T)


def bs_price(option: OptionPosition) -> float:
    S = option.spot_price
    K = option.strike
    r = option.risk_free_rate
    T = option.expiry_days / 365.0
    d1 = _d1(option)
    d2 = _d2(option)
    if option.option_type == OptionType.CALL:
        return S * norm.cdf(d1) - K * math.exp(-r * T) * norm.cdf(d2)
    else:
        return K * math.exp(-r * T) * norm.cdf(-d2) - S * norm.cdf(-d1)


def bs_delta(option: OptionPosition) -> float:
    d1 = _d1(option)
    if option.option_type == OptionType.CALL:
        return float(norm.cdf(d1))
    else:
        return float(norm.cdf(d1) - 1.0)


def bs_gamma(option: OptionPosition) -> float:
    S = option.spot_price
    T = option.expiry_days / 365.0
    vol = option.implied_vol
    d1 = _d1(option)
    return float(norm.pdf(d1) / (S * vol * math.sqrt(T)))


def bs_vega(option: OptionPosition) -> float:
    S = option.spot_price
    T = option.expiry_days / 365.0
    d1 = _d1(option)
    return float(S * norm.pdf(d1) * math.sqrt(T))


def bs_theta(option: OptionPosition) -> float:
    S = option.spot_price
    K = option.strike
    r = option.risk_free_rate
    T = option.expiry_days / 365.0
    vol = option.implied_vol
    d1 = _d1(option)
    d2 = _d2(option)
    common = -(S * norm.pdf(d1) * vol) / (2.0 * math.sqrt(T))
    if option.option_type == OptionType.CALL:
        return float(common - r * K * math.exp(-r * T) * norm.cdf(d2))
    else:
        return float(common + r * K * math.exp(-r * T) * norm.cdf(-d2))


def bs_rho(option: OptionPosition) -> float:
    K = option.strike
    r = option.risk_free_rate
    T = option.expiry_days / 365.0
    d2 = _d2(option)
    if option.option_type == OptionType.CALL:
        return float(K * T * math.exp(-r * T) * norm.cdf(d2))
    else:
        return float(-K * T * math.exp(-r * T) * norm.cdf(-d2))


def bs_vanna(option: OptionPosition) -> float:
    from kinetix_risk.cross_greeks import calculate_vanna
    T = option.expiry_days / 365.0
    return calculate_vanna(option.spot_price, option.strike, T, option.risk_free_rate, option.implied_vol)


def bs_volga(option: OptionPosition) -> float:
    from kinetix_risk.cross_greeks import calculate_volga
    T = option.expiry_days / 365.0
    return calculate_volga(option.spot_price, option.strike, T, option.risk_free_rate, option.implied_vol)


def bs_charm(option: OptionPosition) -> float:
    from kinetix_risk.cross_greeks import calculate_charm
    T = option.expiry_days / 365.0
    return calculate_charm(option.spot_price, option.strike, T, option.risk_free_rate, option.implied_vol, option.option_type)


def bs_greeks(option: OptionPosition) -> dict:
    return {
        "price": bs_price(option),
        "delta": bs_delta(option),
        "gamma": bs_gamma(option),
        "vega": bs_vega(option),
        "theta": bs_theta(option),
        "rho": bs_rho(option),
        "vanna": bs_vanna(option),
        "volga": bs_volga(option),
        "charm": bs_charm(option),
    }
