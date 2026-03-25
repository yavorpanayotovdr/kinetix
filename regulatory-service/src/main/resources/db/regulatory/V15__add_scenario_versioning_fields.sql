ALTER TABLE stress_scenarios
    ADD COLUMN version               INTEGER        NOT NULL DEFAULT 1,
    ADD COLUMN parent_scenario_id    VARCHAR(255),
    ADD COLUMN correlation_override  TEXT,
    ADD COLUMN liquidity_stress_factors TEXT,
    ADD COLUMN historical_period_id  VARCHAR(255),
    ADD COLUMN target_loss           NUMERIC(28, 8);

CREATE INDEX idx_stress_scenarios_parent ON stress_scenarios (parent_scenario_id)
    WHERE parent_scenario_id IS NOT NULL;

CREATE INDEX idx_stress_scenarios_name_version ON stress_scenarios (name, version DESC);
