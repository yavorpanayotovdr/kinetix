CREATE TABLE stress_scenarios (
    id          VARCHAR(255)     NOT NULL,
    name        VARCHAR(255)     NOT NULL,
    description TEXT             NOT NULL,
    shocks      TEXT             NOT NULL,
    status      VARCHAR(30)      NOT NULL,
    created_by  VARCHAR(255)     NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_stress_scenarios PRIMARY KEY (id)
);

CREATE INDEX idx_stress_scenarios_status ON stress_scenarios (status);
CREATE INDEX idx_stress_scenarios_name ON stress_scenarios (name);
