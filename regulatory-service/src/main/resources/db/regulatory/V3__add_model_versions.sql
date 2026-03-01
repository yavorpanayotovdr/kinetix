CREATE TABLE model_versions (
    id          VARCHAR(255)     NOT NULL,
    model_name  VARCHAR(255)     NOT NULL,
    version     VARCHAR(50)      NOT NULL,
    status      VARCHAR(20)      NOT NULL,
    parameters  TEXT             NOT NULL,
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_model_versions PRIMARY KEY (id)
);

CREATE INDEX idx_model_versions_name ON model_versions (model_name);
CREATE INDEX idx_model_versions_status ON model_versions (status);
