-- Alert escalation: track when and to whom an alert was escalated.
ALTER TABLE alert_events ADD COLUMN acknowledged_at TIMESTAMPTZ;
ALTER TABLE alert_events ADD COLUMN escalated_at TIMESTAMPTZ;
ALTER TABLE alert_events ADD COLUMN escalated_to VARCHAR(255);

-- Index for the escalation scheduler query: quickly find ACKNOWLEDGED alerts older than the timeout.
CREATE INDEX idx_alert_events_acknowledged ON alert_events (acknowledged_at) WHERE status = 'ACKNOWLEDGED';
