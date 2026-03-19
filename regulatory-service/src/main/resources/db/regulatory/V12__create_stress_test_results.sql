CREATE TABLE IF NOT EXISTS stress_test_results (
    id              VARCHAR(255)     NOT NULL,
    scenario_id     VARCHAR(255)     NOT NULL REFERENCES stress_scenarios(id),
    portfolio_id    VARCHAR(255)     NOT NULL,
    calculated_at   TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    base_pv         NUMERIC(28,8)    NULL,
    stressed_pv     NUMERIC(28,8)    NULL,
    pnl_impact       NUMERIC(28,8)    NULL,
    var_impact       DOUBLE PRECISION NULL,
    position_impacts JSONB            NULL,
    model_version   VARCHAR(100)     NULL,
    CONSTRAINT pk_stress_test_results PRIMARY KEY (id)
);

CREATE INDEX idx_stress_results_scenario
    ON stress_test_results (scenario_id, calculated_at DESC);

CREATE INDEX idx_stress_results_portfolio
    ON stress_test_results (portfolio_id, calculated_at DESC);
