ALTER TABLE calculation_jobs RENAME TO valuation_jobs;
DROP INDEX IF EXISTS idx_calc_jobs_portfolio_started;
CREATE INDEX idx_val_jobs_portfolio_started ON valuation_jobs (portfolio_id, started_at DESC);
