ALTER TABLE calculation_runs RENAME TO calculation_jobs;
ALTER TABLE calculation_jobs RENAME COLUMN run_id TO job_id;
DROP INDEX IF EXISTS idx_calc_runs_portfolio_started;
CREATE INDEX idx_calc_jobs_portfolio_started ON calculation_jobs (portfolio_id, started_at DESC);
