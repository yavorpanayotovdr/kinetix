CREATE TABLE regulatory_submissions (
    id              VARCHAR(255)     NOT NULL,
    report_type     VARCHAR(100)     NOT NULL,
    status          VARCHAR(30)      NOT NULL,
    preparer_id     VARCHAR(255)     NOT NULL,
    approver_id     VARCHAR(255),
    deadline        TIMESTAMPTZ      NOT NULL,
    submitted_at    TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_regulatory_submissions PRIMARY KEY (id)
);

CREATE INDEX idx_submissions_status ON regulatory_submissions (status);
CREATE INDEX idx_submissions_deadline ON regulatory_submissions (deadline);
