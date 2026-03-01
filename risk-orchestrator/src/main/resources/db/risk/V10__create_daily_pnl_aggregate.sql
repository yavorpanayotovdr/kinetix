-- Convert pnl_attributions to a hypertable so we can build a continuous
-- aggregate on top of it.

-- TimescaleDB requires the time column in every unique constraint.
-- Drop the existing unique constraint and PK, then recreate with the
-- time column included.
ALTER TABLE pnl_attributions DROP CONSTRAINT pnl_attributions_pkey;
ALTER TABLE pnl_attributions DROP CONSTRAINT pnl_attributions_portfolio_id_attribution_date_key;
ALTER TABLE pnl_attributions ADD CONSTRAINT uq_pnl_attributions
    UNIQUE (id, attribution_date);
ALTER TABLE pnl_attributions ADD CONSTRAINT uq_pnl_portfolio_date
    UNIQUE (portfolio_id, attribution_date);

SELECT create_hypertable('pnl_attributions', 'attribution_date', migrate_data => true);

-- Continuous aggregate: daily P&L rollup per portfolio.
-- Provides a pre-computed summary suitable for reporting dashboards.
CREATE MATERIALIZED VIEW daily_pnl_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', attribution_date) AS bucket,
    portfolio_id,
    SUM(total_pnl)        AS total_pnl,
    SUM(delta_pnl)        AS total_delta_pnl,
    SUM(gamma_pnl)        AS total_gamma_pnl,
    SUM(vega_pnl)         AS total_vega_pnl,
    SUM(theta_pnl)        AS total_theta_pnl,
    SUM(rho_pnl)          AS total_rho_pnl,
    SUM(unexplained_pnl)  AS total_unexplained_pnl,
    COUNT(*)              AS attribution_count
FROM pnl_attributions
GROUP BY bucket, portfolio_id
WITH NO DATA;

-- Refresh policy: refresh daily, looking back 3 days for corrections.
SELECT add_continuous_aggregate_policy('daily_pnl_summary',
    start_offset  => INTERVAL '3 days',
    end_offset    => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day'
);
