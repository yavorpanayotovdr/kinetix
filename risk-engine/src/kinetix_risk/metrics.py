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

risk_var_expected_shortfall = Gauge(
    "risk_var_expected_shortfall",
    "Current Expected Shortfall (CVaR) value for a portfolio",
    ["portfolio_id"],
)

risk_var_component_contribution = Gauge(
    "risk_var_component_contribution",
    "VaR contribution by asset class for a portfolio",
    ["portfolio_id", "asset_class"],
)

ml_prediction_duration_seconds = Histogram(
    "ml_prediction_duration_seconds",
    "Duration of ML model prediction in seconds",
    ["model_type"],
)

ml_prediction_total = Counter(
    "ml_prediction_total",
    "Total number of ML model predictions",
    ["model_type"],
)

ml_anomaly_detected_total = Counter(
    "ml_anomaly_detected_total",
    "Total number of anomalies detected",
    ["metric_name"],
)

stress_test_duration_seconds = Histogram(
    "stress_test_duration_seconds",
    "Duration of stress test calculation in seconds",
    ["scenario_name"],
)

stress_test_total = Counter(
    "stress_test_total",
    "Total number of stress tests run",
    ["scenario_name"],
)

greeks_calculation_duration_seconds = Histogram(
    "greeks_calculation_duration_seconds",
    "Duration of Greeks calculation in seconds",
)

greeks_calculation_total = Counter(
    "greeks_calculation_total",
    "Total number of Greeks calculations",
)

frtb_calculation_duration_seconds = Histogram(
    "frtb_calculation_duration_seconds",
    "Duration of FRTB calculation in seconds",
)

frtb_calculation_total = Counter(
    "frtb_calculation_total",
    "Total number of FRTB calculations",
)

regulatory_report_total = Counter(
    "regulatory_report_total",
    "Total number of regulatory reports generated",
    ["format"],
)
