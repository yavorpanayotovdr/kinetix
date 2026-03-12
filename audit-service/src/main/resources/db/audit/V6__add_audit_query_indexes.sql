-- Support time-windowed audit queries once /events is paginated.
CREATE INDEX IF NOT EXISTS idx_audit_events_received_at
    ON audit_events (received_at DESC);

-- Support portfolio + time window queries:
-- WHERE portfolio_id = ? AND received_at >= ? ORDER BY received_at DESC
CREATE INDEX IF NOT EXISTS idx_audit_events_portfolio_received_at
    ON audit_events (portfolio_id, received_at DESC);
