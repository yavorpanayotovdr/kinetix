-- Continuous aggregate: hourly VaR averages per portfolio.
-- Enables fast time-series dashboards without scanning raw valuation rows.
CREATE MATERIALIZED VIEW hourly_var_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', started_at) AS bucket,
    portfolio_id,
    AVG(var_value)            AS avg_var,
    MIN(var_value)            AS min_var,
    MAX(var_value)            AS max_var,
    AVG(expected_shortfall)   AS avg_es,
    COUNT(*)                  AS job_count
FROM valuation_jobs
WHERE var_value IS NOT NULL
GROUP BY bucket, portfolio_id
WITH NO DATA;

-- Refresh policy: keep the aggregate up-to-date, refreshing data from the
-- last 3 hours (to account for late-arriving rows) every 30 minutes.
SELECT add_continuous_aggregate_policy('hourly_var_summary',
    start_offset  => INTERVAL '3 hours',
    end_offset    => INTERVAL '1 hour',
    schedule_interval => INTERVAL '30 minutes'
);
