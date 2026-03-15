-- Single-column job_id index for efficient point lookups from
-- official_eod_designations joins. The existing UNIQUE(job_id, started_at)
-- composite index requires started_at for chunk exclusion; this index
-- serves pure job_id lookups without a timestamp hint.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_valuation_jobs_job_id
    ON valuation_jobs (job_id);

-- Reverse-lookup index on official_eod_designations for cross-portfolio
-- queries (e.g., "which portfolios are missing EOD today?").
CREATE INDEX IF NOT EXISTS idx_eod_designations_date
    ON official_eod_designations (valuation_date DESC, portfolio_id);

-- Materialised view: pre-joins official_eod_designations with
-- valuation_jobs scalar columns. Eliminates compressed-chunk scans
-- for all timeline queries.
CREATE MATERIALIZED VIEW IF NOT EXISTS daily_official_eod_summary AS
SELECT
    d.portfolio_id,
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
ORDER BY d.portfolio_id, d.valuation_date;

CREATE UNIQUE INDEX idx_daily_official_eod_summary_pk
    ON daily_official_eod_summary (portfolio_id, valuation_date);
