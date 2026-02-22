CREATE TABLE alert_events (
    id             VARCHAR(255)     NOT NULL,
    rule_id        VARCHAR(255)     NOT NULL,
    rule_name      VARCHAR(255)     NOT NULL,
    type           VARCHAR(50)      NOT NULL,
    severity       VARCHAR(50)      NOT NULL,
    message        TEXT             NOT NULL,
    current_value  DOUBLE PRECISION NOT NULL,
    threshold      DOUBLE PRECISION NOT NULL,
    portfolio_id   VARCHAR(255)     NOT NULL,
    triggered_at   TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_alert_events PRIMARY KEY (id)
);

CREATE INDEX idx_alert_events_portfolio ON alert_events (portfolio_id);
CREATE INDEX idx_alert_events_rule ON alert_events (rule_id);
CREATE INDEX idx_alert_events_triggered_at ON alert_events (triggered_at DESC);
