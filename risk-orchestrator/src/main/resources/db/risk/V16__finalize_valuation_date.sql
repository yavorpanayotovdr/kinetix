ALTER TABLE valuation_jobs ALTER COLUMN valuation_date SET NOT NULL;
ALTER TABLE valuation_jobs ALTER COLUMN valuation_date SET DEFAULT (NOW() AT TIME ZONE 'UTC')::date;

-- Partial index: only completed jobs are ever queried by date.
-- Covers findLatestCompletedByDate(portfolioId, valuationDate) efficiently.
CREATE INDEX IF NOT EXISTS idx_valuation_jobs_completed_by_date
    ON valuation_jobs (portfolio_id, valuation_date, started_at DESC)
    WHERE status = 'COMPLETED';
