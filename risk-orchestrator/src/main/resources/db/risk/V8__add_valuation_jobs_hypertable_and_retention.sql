-- Enable TimescaleDB extension (idempotent).
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- TimescaleDB requires the partitioning column (started_at) to be part of
-- every unique constraint. Drop the single-column PK and replace it with a
-- composite unique constraint that includes both job_id and started_at.
ALTER TABLE valuation_jobs DROP CONSTRAINT calculation_runs_pkey;
ALTER TABLE valuation_jobs ADD CONSTRAINT uq_valuation_jobs UNIQUE (job_id, started_at);

-- Convert to a hypertable partitioned on started_at.
SELECT create_hypertable('valuation_jobs', 'started_at', migrate_data => true);

-- Retain detailed valuation records for 1 year; older chunks are dropped.
SELECT add_retention_policy('valuation_jobs', INTERVAL '1 year');
