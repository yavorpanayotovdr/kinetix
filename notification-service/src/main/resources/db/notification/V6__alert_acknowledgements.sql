-- Separate table for acknowledgements to avoid hypertable UPDATE compression cost.
CREATE TABLE alert_acknowledgements (
    id                  VARCHAR(255) NOT NULL,
    alert_event_id      VARCHAR(255) NOT NULL,
    alert_triggered_at  TIMESTAMPTZ  NOT NULL,
    acknowledged_by     VARCHAR(255) NOT NULL,
    acknowledged_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    notes               TEXT,

    CONSTRAINT pk_alert_acknowledgements PRIMARY KEY (id)
);

CREATE INDEX idx_alert_acks_event ON alert_acknowledgements (alert_event_id, alert_triggered_at);
