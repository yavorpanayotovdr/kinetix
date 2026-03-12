-- Stores the last successfully verified audit event id and its hash.
-- Allows incremental chain verification to resume from the last checkpoint
-- rather than replaying the entire trail on every call.
CREATE TABLE IF NOT EXISTS audit_verification_checkpoints (
    id              BIGSERIAL    PRIMARY KEY,
    last_event_id   BIGINT       NOT NULL,
    last_hash       VARCHAR(64)  NOT NULL,
    verified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    event_count     BIGINT       NOT NULL
);

CREATE INDEX idx_verification_checkpoints_verified_at
    ON audit_verification_checkpoints (verified_at DESC);
