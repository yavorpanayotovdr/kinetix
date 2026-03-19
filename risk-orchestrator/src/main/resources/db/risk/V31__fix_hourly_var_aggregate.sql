-- Recreate hourly_var_summary with calculation_type in GROUP BY so that
-- PARAMETRIC and MONTE_CARLO VaR values are not merged into a single average.
-- Without this, the aggregate is meaningless for mixed-method portfolios.

SELECT remove_continuous_aggregate_policy('hourly_var_summary');

DROP MATERIALIZED VIEW IF EXISTS hourly_var_summary;

CREATE MATERIALIZED VIEW hourly_var_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', started_at) AS bucket,
    portfolio_id,
    calculation_type,
    AVG(var_value) AS avg_var,
    MIN(var_value) AS min_var,
    MAX(var_value) AS max_var,
    AVG(expected_shortfall) AS avg_es,
    COUNT(*) AS job_count
FROM valuation_jobs
WHERE var_value IS NOT NULL
GROUP BY bucket, portfolio_id, calculation_type
WITH NO DATA;

SELECT add_continuous_aggregate_policy('hourly_var_summary',
    start_offset  => INTERVAL '3 hours',
    end_offset    => INTERVAL '1 hour',
    schedule_interval => INTERVAL '30 minutes'
);
