-- Regulatory requirement: risk calculation records must be available for 7 years.
-- Previous policy: 1 year (V8). Align with audit_events retention.
SELECT remove_retention_policy('valuation_jobs');
SELECT add_retention_policy('valuation_jobs', INTERVAL '7 years');

-- Add compression for cost management (chunks older than 90 days)
SELECT add_compression_policy('valuation_jobs', INTERVAL '90 days');
