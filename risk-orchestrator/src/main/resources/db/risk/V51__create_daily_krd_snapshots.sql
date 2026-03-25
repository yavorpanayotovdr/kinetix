-- Daily KRD snapshots: one row per book/instrument/tenor/date.
-- KRD is computed at end-of-day cadence, not intraday, so this is a
-- plain table rather than a TimescaleDB hypertable.
CREATE TABLE daily_krd_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    book_id         VARCHAR(128) NOT NULL,
    instrument_id   VARCHAR(128) NOT NULL,
    snapshot_date   DATE         NOT NULL,
    tenor_label     VARCHAR(16)  NOT NULL,
    dv01            NUMERIC(20, 6) NOT NULL,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_daily_krd_snapshots_book_date
    ON daily_krd_snapshots (book_id, snapshot_date DESC);

CREATE INDEX idx_daily_krd_snapshots_instrument_date
    ON daily_krd_snapshots (instrument_id, snapshot_date DESC);
