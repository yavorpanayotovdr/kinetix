-- V22 rollback: Remove replay_runs table and manifest output columns
-- NOT Flyway-managed. Execute manually in a maintenance window.
-- WARNING: This is destructive. Back up replay_runs before executing.

DROP INDEX IF EXISTS idx_replay_runs_divergent;
DROP INDEX IF EXISTS idx_replay_runs_original_job_id;
DROP INDEX IF EXISTS idx_replay_runs_replayed_at;
DROP INDEX IF EXISTS idx_replay_runs_manifest_id;
DROP TABLE IF EXISTS replay_runs;

DROP INDEX IF EXISTS idx_run_manifests_input_digest;
DROP INDEX IF EXISTS idx_run_manifests_output_digest;

ALTER TABLE run_manifests
    DROP COLUMN IF EXISTS output_digest,
    DROP COLUMN IF EXISTS expected_shortfall,
    DROP COLUMN IF EXISTS var_value;
