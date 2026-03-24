-- Satisfies spec invariant AuditBothParameters (specs/regime.allium):
-- every VaR job must record BOTH the requested parameters AND the effective
-- (regime-adjusted) parameters so that audit can reconstruct what was asked
-- vs. what was actually computed.
--
-- time_horizon_days: the effective time horizon used in this run
-- requested_calculation_type: the calc type as originally requested (non-null only
--   when a regime override changed the requested value)
-- requested_confidence_level: the confidence level as originally requested
-- requested_time_horizon_days: the time horizon as originally requested

ALTER TABLE valuation_jobs
    ADD COLUMN IF NOT EXISTS time_horizon_days            INTEGER,
    ADD COLUMN IF NOT EXISTS requested_calculation_type  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS requested_confidence_level  VARCHAR(10),
    ADD COLUMN IF NOT EXISTS requested_time_horizon_days INTEGER;
