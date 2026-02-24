CREATE TABLE calculation_runs (
    run_id             UUID           PRIMARY KEY,
    portfolio_id       VARCHAR(255)   NOT NULL,
    trigger_type       VARCHAR(50)    NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    started_at         TIMESTAMPTZ    NOT NULL,
    completed_at       TIMESTAMPTZ,
    duration_ms        BIGINT,
    calculation_type   VARCHAR(50),
    confidence_level   VARCHAR(10),
    var_value          DOUBLE PRECISION,
    expected_shortfall DOUBLE PRECISION,
    steps              JSONB          NOT NULL DEFAULT '[]',
    error              TEXT
);

CREATE INDEX idx_calc_runs_portfolio_started
    ON calculation_runs (portfolio_id, started_at DESC);
