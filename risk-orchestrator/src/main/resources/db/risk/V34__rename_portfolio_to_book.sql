-- V33: Rename portfolio_id → book_id across all risk-orchestrator tables.
-- Continuous aggregates and materialized views that reference portfolio_id must be
-- dropped and recreated because TimescaleDB does not support renaming a column that
-- is referenced in a continuous aggregate.

-- ============================================================
-- 1. valuation_jobs (TimescaleDB hypertable with continuous aggregate)
-- ============================================================

-- 1a. Suspend compression so we can alter the hypertable.
SELECT remove_compression_policy('valuation_jobs', if_exists => true);

DO $$
DECLARE chunk REGCLASS;
BEGIN
    FOR chunk IN
        SELECT format('%I.%I', c.chunk_schema, c.chunk_name)::regclass
        FROM timescaledb_information.chunks c
        WHERE c.hypertable_name = 'valuation_jobs'
          AND c.is_compressed = true
    LOOP
        PERFORM decompress_chunk(chunk);
    END LOOP;
END $$;

-- 1b. Drop the hourly_var_summary continuous aggregate (references portfolio_id).
SELECT remove_continuous_aggregate_policy('hourly_var_summary', if_exists => true);
DROP MATERIALIZED VIEW IF EXISTS hourly_var_summary;

-- 1c. Drop indexes on valuation_jobs that reference portfolio_id.
DROP INDEX IF EXISTS idx_valuation_jobs_official_eod;

-- 1d. Rename the column.
ALTER TABLE valuation_jobs RENAME COLUMN portfolio_id TO book_id;

-- 1e. Recreate the partial index with the new column name.
CREATE INDEX IF NOT EXISTS idx_valuation_jobs_official_eod
    ON valuation_jobs (book_id, valuation_date)
    WHERE run_label = 'OFFICIAL_EOD';

-- 1f. Recreate hourly_var_summary referencing book_id.
CREATE MATERIALIZED VIEW hourly_var_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', started_at) AS bucket,
    book_id,
    calculation_type,
    AVG(var_value) AS avg_var,
    MIN(var_value) AS min_var,
    MAX(var_value) AS max_var,
    AVG(expected_shortfall) AS avg_es,
    COUNT(*) AS job_count
FROM valuation_jobs
WHERE var_value IS NOT NULL
GROUP BY bucket, book_id, calculation_type
WITH NO DATA;

SELECT add_continuous_aggregate_policy('hourly_var_summary',
    start_offset  => INTERVAL '3 hours',
    end_offset    => INTERVAL '1 hour',
    schedule_interval => INTERVAL '30 minutes'
);

-- 1g. Re-enable compression policy.
SELECT add_compression_policy('valuation_jobs', INTERVAL '90 days', if_not_exists => true);

-- ============================================================
-- 2. pnl_attributions (TimescaleDB hypertable with continuous aggregate)
-- ============================================================

-- 2a. Drop the daily_pnl_summary continuous aggregate (references portfolio_id).
SELECT remove_continuous_aggregate_policy('daily_pnl_summary', if_exists => true);
DROP MATERIALIZED VIEW IF EXISTS daily_pnl_summary;

-- 2b. Drop the unique constraint that references portfolio_id.
ALTER TABLE pnl_attributions DROP CONSTRAINT IF EXISTS uq_pnl_portfolio_date;

-- 2c. Rename the column.
ALTER TABLE pnl_attributions RENAME COLUMN portfolio_id TO book_id;

-- 2d. Recreate the unique constraint with the new column name.
ALTER TABLE pnl_attributions
    ADD CONSTRAINT uq_pnl_book_date UNIQUE (book_id, attribution_date);

-- 2e. Recreate daily_pnl_summary referencing book_id.
CREATE MATERIALIZED VIEW daily_pnl_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', attribution_date) AS bucket,
    book_id,
    SUM(total_pnl)        AS total_pnl,
    SUM(delta_pnl)        AS total_delta_pnl,
    SUM(gamma_pnl)        AS total_gamma_pnl,
    SUM(vega_pnl)         AS total_vega_pnl,
    SUM(theta_pnl)        AS total_theta_pnl,
    SUM(rho_pnl)          AS total_rho_pnl,
    SUM(unexplained_pnl)  AS total_unexplained_pnl,
    COUNT(*)              AS attribution_count
FROM pnl_attributions
GROUP BY bucket, book_id
WITH NO DATA;

SELECT add_continuous_aggregate_policy('daily_pnl_summary',
    start_offset  => INTERVAL '3 days',
    end_offset    => INTERVAL '1 day',
    schedule_interval => INTERVAL '1 day'
);

-- ============================================================
-- 3. daily_risk_snapshots (TimescaleDB hypertable, no continuous aggregate)
-- ============================================================

-- 3a. Drop the composite index that references portfolio_id.
DROP INDEX IF EXISTS idx_daily_risk_snapshots_portfolio_date;

-- 3b. Drop the unique constraint that references portfolio_id.
ALTER TABLE daily_risk_snapshots DROP CONSTRAINT IF EXISTS daily_risk_snapshots_portfolio_id_snapshot_date_instrument_id_key;

-- 3c. Rename the column.
ALTER TABLE daily_risk_snapshots RENAME COLUMN portfolio_id TO book_id;

-- 3d. Recreate the unique constraint and index with the new column name.
ALTER TABLE daily_risk_snapshots
    ADD CONSTRAINT uq_daily_risk_snapshots_book_date_instrument
    UNIQUE (book_id, snapshot_date, instrument_id);

CREATE INDEX idx_daily_risk_snapshots_book_date
    ON daily_risk_snapshots (book_id, snapshot_date DESC);

-- ============================================================
-- 4. sod_baselines (standard table)
-- ============================================================

ALTER TABLE sod_baselines DROP CONSTRAINT IF EXISTS sod_baselines_portfolio_id_baseline_date_key;

ALTER TABLE sod_baselines RENAME COLUMN portfolio_id TO book_id;

ALTER TABLE sod_baselines
    ADD CONSTRAINT uq_sod_baselines_book_date UNIQUE (book_id, baseline_date);

-- ============================================================
-- 5. run_manifests (standard table)
-- ============================================================

DROP INDEX IF EXISTS idx_run_manifests_portfolio_date;

ALTER TABLE run_manifests RENAME COLUMN portfolio_id TO book_id;

CREATE INDEX IF NOT EXISTS idx_run_manifests_book_date
    ON run_manifests (book_id, valuation_date);

-- ============================================================
-- 6. official_eod_designations (standard table)
-- ============================================================

-- The PK is (portfolio_id, valuation_date) — must recreate it.
ALTER TABLE official_eod_designations DROP CONSTRAINT IF EXISTS official_eod_designations_pkey;

-- Drop the index on valuation_date that includes portfolio_id.
DROP INDEX IF EXISTS idx_eod_designations_date;

ALTER TABLE official_eod_designations RENAME COLUMN portfolio_id TO book_id;

ALTER TABLE official_eod_designations
    ADD CONSTRAINT official_eod_designations_pkey PRIMARY KEY (book_id, valuation_date);

CREATE INDEX IF NOT EXISTS idx_eod_designations_date
    ON official_eod_designations (valuation_date DESC, book_id);

-- ============================================================
-- 7. daily_official_eod_summary (materialized view — references portfolio_id)
-- ============================================================

DROP MATERIALIZED VIEW IF EXISTS daily_official_eod_summary;
DROP INDEX IF EXISTS idx_daily_official_eod_summary_pk;

CREATE MATERIALIZED VIEW daily_official_eod_summary AS
SELECT
    d.book_id,
    d.valuation_date,
    d.job_id,
    vj.var_value,
    vj.expected_shortfall,
    vj.pv_value,
    vj.delta,
    vj.gamma,
    vj.vega,
    vj.theta,
    vj.rho,
    vj.duration_ms,
    vj.calculation_type,
    vj.confidence_level,
    d.promoted_at,
    d.promoted_by
FROM official_eod_designations d
JOIN valuation_jobs vj ON vj.job_id = d.job_id
ORDER BY d.book_id, d.valuation_date;

CREATE UNIQUE INDEX idx_daily_official_eod_summary_pk
    ON daily_official_eod_summary (book_id, valuation_date);
