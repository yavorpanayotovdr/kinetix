CREATE TABLE IF NOT EXISTS hedge_recommendations (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    book_id                 VARCHAR(255) NOT NULL,
    target_metric           VARCHAR(20)  NOT NULL,
    target_reduction_pct    NUMERIC(8, 6) NOT NULL,
    requested_at            TIMESTAMPTZ  NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    expires_at              TIMESTAMPTZ  NOT NULL,
    accepted_by             VARCHAR(255),
    accepted_at             TIMESTAMPTZ,
    source_job_id           VARCHAR(255),
    constraints_json        JSONB        NOT NULL DEFAULT '{}',
    suggestions_json        JSONB        NOT NULL DEFAULT '[]',
    pre_hedge_greeks_json   JSONB        NOT NULL DEFAULT '{}',

    PRIMARY KEY (id)
);

CREATE INDEX idx_hedge_recommendations_book_id_requested_at
    ON hedge_recommendations (book_id, requested_at DESC);

CREATE INDEX idx_hedge_recommendations_status
    ON hedge_recommendations (status)
    WHERE status = 'PENDING';
