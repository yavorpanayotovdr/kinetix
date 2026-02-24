CREATE TABLE IF NOT EXISTS correlation_matrices (
    id            BIGSERIAL    PRIMARY KEY,
    labels        TEXT         NOT NULL,
    values        TEXT         NOT NULL,
    window_days   INTEGER      NOT NULL,
    as_of_date    TIMESTAMPTZ  NOT NULL,
    method        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_correlation_labels_window ON correlation_matrices (labels, window_days);
CREATE INDEX idx_correlation_as_of ON correlation_matrices (as_of_date);
