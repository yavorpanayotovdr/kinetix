-- Cross-book aggregated VaR job history.
-- Records each cross-book VaR calculation with its constituent books,
-- aggregate results, and per-book contribution breakdown.
CREATE TABLE cross_book_valuation_jobs (
    job_id              UUID            NOT NULL,
    group_id            VARCHAR(255)    NOT NULL,
    book_ids            JSONB           NOT NULL,
    trigger_type        VARCHAR(50)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    valuation_date      DATE            NOT NULL,
    started_at          TIMESTAMPTZ     NOT NULL,
    completed_at        TIMESTAMPTZ,
    duration_ms         BIGINT,
    calculation_type    VARCHAR(50),
    confidence_level    VARCHAR(10),
    var_value           DOUBLE PRECISION,
    expected_shortfall  DOUBLE PRECISION,
    total_standalone_var    DOUBLE PRECISION,
    diversification_benefit DOUBLE PRECISION,
    component_breakdown JSONB,
    book_contributions  JSONB,
    phases              JSONB           NOT NULL DEFAULT '[]',
    current_phase       VARCHAR(50),
    error               TEXT,
    PRIMARY KEY (job_id)
);

CREATE INDEX idx_cross_book_jobs_group_date
    ON cross_book_valuation_jobs (group_id, valuation_date DESC);
