-- V22: Add output columns to run_manifests and create replay_runs audit table
-- Supports regulatory requirement to reconstruct both inputs and outputs
-- of any risk calculation, and to audit replay attempts.

-- ============================================================
-- Step 1: Add output columns to run_manifests
-- ============================================================
-- var_value and expected_shortfall are nullable because:
--   - existing rows have no output values
--   - FAILED manifests may never produce outputs
--   - manifests are initially created with INPUTS_FROZEN status before valuation
-- output_digest is a SHA-256 of the outputs, consistent with input_digest.

ALTER TABLE run_manifests
    ADD COLUMN IF NOT EXISTS var_value          DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS expected_shortfall DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS output_digest      CHAR(64)        NULL;

-- Partial index on output_digest for drift analysis queries.
-- Only completed runs (non-null output_digest) are indexed.
CREATE INDEX IF NOT EXISTS idx_run_manifests_output_digest
    ON run_manifests (output_digest)
    WHERE output_digest IS NOT NULL;

-- Index on input_digest for reproducibility verification queries.
-- Missing from V20 — needed for "find all manifests with identical inputs".
CREATE INDEX IF NOT EXISTS idx_run_manifests_input_digest
    ON run_manifests (input_digest);

-- ============================================================
-- Step 2: Create replay_runs table
-- ============================================================
-- Append-only audit log of every replay execution.
-- Rows are never updated after insert.
-- Retention: 7 years, consistent with run_manifests.
--
-- Original values are denormalised at insert time so the audit record
-- is self-contained and does not depend on run_manifests being unchanged.
-- var_drift and es_drift are generated columns for efficient threshold queries.

CREATE TABLE IF NOT EXISTS replay_runs (
    replay_id                   UUID            NOT NULL,
    manifest_id                 UUID            NOT NULL
        REFERENCES run_manifests (manifest_id),
    original_job_id             UUID            NOT NULL,
    replayed_at                 TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    triggered_by                VARCHAR(255)    NOT NULL,

    -- Replay engine outputs
    replay_var_value            DOUBLE PRECISION NULL,
    replay_expected_shortfall   DOUBLE PRECISION NULL,
    replay_model_version        VARCHAR(100)    NULL,
    replay_output_digest        CHAR(64)        NULL,

    -- Original outputs (denormalised from run_manifests at insert time)
    original_var_value          DOUBLE PRECISION NULL,
    original_expected_shortfall DOUBLE PRECISION NULL,

    -- Input digest verification
    input_digest_match          BOOLEAN         NOT NULL,
    original_input_digest       CHAR(64)        NOT NULL,
    replay_input_digest         CHAR(64)        NOT NULL,

    -- Pre-computed absolute drift (generated, stored at insert)
    var_drift   DOUBLE PRECISION GENERATED ALWAYS AS (
        ABS(replay_var_value - original_var_value)
    ) STORED,
    es_drift    DOUBLE PRECISION GENERATED ALWAYS AS (
        ABS(replay_expected_shortfall - original_expected_shortfall)
    ) STORED,

    CONSTRAINT pk_replay_runs PRIMARY KEY (replay_id)
);

-- All replays for a given manifest, most recent first
CREATE INDEX IF NOT EXISTS idx_replay_runs_manifest_id
    ON replay_runs (manifest_id, replayed_at DESC);

-- Time-range queries for regulatory reporting
CREATE INDEX IF NOT EXISTS idx_replay_runs_replayed_at
    ON replay_runs (replayed_at DESC);

-- Lookup by original job
CREATE INDEX IF NOT EXISTS idx_replay_runs_original_job_id
    ON replay_runs (original_job_id);

-- Partial index for divergent replays (drift detection alerts)
CREATE INDEX IF NOT EXISTS idx_replay_runs_divergent
    ON replay_runs (replayed_at DESC, manifest_id)
    WHERE input_digest_match = false OR var_drift > 0;
