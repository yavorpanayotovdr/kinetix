-- Fix the portfolio → book rename to align with the rest of the platform.
ALTER TABLE alert_events RENAME COLUMN portfolio_id TO book_id;

-- Timestamps on alert_rules (missing from V1).
ALTER TABLE alert_rules ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE alert_rules ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Update index to use the new column name.
DROP INDEX IF EXISTS idx_alert_events_portfolio;
CREATE INDEX idx_alert_events_book_time ON alert_events (book_id, triggered_at DESC);
