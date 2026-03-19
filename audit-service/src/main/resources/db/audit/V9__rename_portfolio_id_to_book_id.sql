ALTER TABLE audit_events RENAME COLUMN portfolio_id TO book_id;

DROP INDEX IF EXISTS idx_audit_events_portfolio_received_at;

-- Update TimescaleDB compression segmentby to use renamed column.
ALTER TABLE audit_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'book_id',
    timescaledb.compress_orderby = 'received_at DESC'
);

CREATE INDEX idx_audit_events_book ON audit_events (book_id);
CREATE INDEX idx_audit_events_book_received_at ON audit_events (book_id, received_at DESC);
