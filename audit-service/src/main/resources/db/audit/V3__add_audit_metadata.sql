ALTER TABLE audit_events ADD COLUMN user_id VARCHAR(255);
ALTER TABLE audit_events ADD COLUMN user_role VARCHAR(100);
ALTER TABLE audit_events ADD COLUMN event_type VARCHAR(100) NOT NULL DEFAULT 'TRADE_BOOKED';

CREATE INDEX idx_audit_events_user_id ON audit_events (user_id);
CREATE INDEX idx_audit_events_event_type ON audit_events (event_type);
