-- Add column for AI-generated action suggestions.
ALTER TABLE alert_events ADD COLUMN suggested_action TEXT;
