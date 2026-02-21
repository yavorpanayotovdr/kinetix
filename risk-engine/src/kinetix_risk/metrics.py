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
