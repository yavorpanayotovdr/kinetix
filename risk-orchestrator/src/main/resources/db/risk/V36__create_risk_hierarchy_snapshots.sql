-- V36: Create risk_hierarchy_snapshots hypertable.
-- Persists per-hierarchy-node risk metrics for each computation run.
-- Enables historical trending of VaR at firm, division, desk, and book levels.
-- top_contributors: JSONB array of up to 5 child entities by |var_contribution|.
-- 365-day retention per the hierarchy-risk.allium config.

CREATE TABLE risk_hierarchy_snapshots (
    id                   BIGSERIAL,
    snapshot_at          TIMESTAMPTZ      NOT NULL,
    level                VARCHAR(16)      NOT NULL,
    entity_id            VARCHAR(64)      NOT NULL,
    parent_id            VARCHAR(64),
    var_value            NUMERIC(24, 6)   NOT NULL,
    expected_shortfall   NUMERIC(24, 6),
    pnl_today            NUMERIC(24, 6),
    limit_utilisation    NUMERIC(10, 6),
    marginal_var         NUMERIC(24, 6),
    top_contributors     JSONB            NOT NULL DEFAULT '[]',
    is_partial           BOOLEAN          NOT NULL DEFAULT false,
    PRIMARY KEY (id, snapshot_at)
);

SELECT create_hypertable(
    'risk_hierarchy_snapshots',
    'snapshot_at',
    chunk_time_interval => INTERVAL '1 day',
    migrate_data        => true
);

CREATE INDEX idx_risk_hierarchy_entity_time
    ON risk_hierarchy_snapshots (level, entity_id, snapshot_at DESC);

SELECT add_retention_policy('risk_hierarchy_snapshots', INTERVAL '365 days');
