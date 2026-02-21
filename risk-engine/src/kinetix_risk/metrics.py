from prometheus_client import Counter, Gauge, Histogram

risk_var_calculation_duration_seconds = Histogram(
    "risk_var_calculation_duration_seconds",
    "Duration of VaR calculation in seconds",
)

risk_var_calculation_total = Counter(
    "risk_var_calculation_total",
    "Total number of VaR calculations",
    ["calculation_type", "confidence_level"],
)

risk_var_value = Gauge(
    "risk_var_value",
    "Current VaR value for a portfolio",
    ["portfolio_id"],
)
