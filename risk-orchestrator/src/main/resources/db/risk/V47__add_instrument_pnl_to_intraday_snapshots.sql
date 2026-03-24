ALTER TABLE intraday_pnl_snapshots
    ADD COLUMN IF NOT EXISTS instrument_pnl_json TEXT NOT NULL DEFAULT '[]';
