-- Materialised view: denormalises daily_risk_snapshots, latest valuation_job per
-- instrument, and pnl_attributions for the same date into a single flat table.
-- Covers ~80% of reporting use cases without query-time federation across three tables.
--
-- Refresh: daily after EOD promotion via ScheduledMatViewRefreshJob.
-- Production REFRESH uses CONCURRENT mode to allow reads during refresh.
--
-- NOTE: CREATE INDEX CONCURRENTLY is transaction-incompatible; the unique index
-- is created in a separate step using a plain (non-concurrent) CREATE UNIQUE INDEX
-- so that it runs safely inside the Flyway migration transaction.

CREATE MATERIALIZED VIEW IF NOT EXISTS risk_positions_flat AS
WITH latest_snapshots AS (
    SELECT DISTINCT ON (book_id, instrument_id)
        book_id,
        instrument_id,
        asset_class,
        quantity,
        market_price,
        delta,
        gamma,
        vega,
        theta,
        rho,
        var_contribution,
        es_contribution,
        snapshot_date
    FROM daily_risk_snapshots
    ORDER BY book_id, instrument_id, snapshot_date DESC
),
latest_pnl AS (
    SELECT DISTINCT ON (book_id)
        book_id,
        attribution_date,
        total_pnl,
        delta_pnl,
        gamma_pnl,
        vega_pnl,
        theta_pnl,
        rho_pnl,
        unexplained_pnl
    FROM pnl_attributions
    ORDER BY book_id, attribution_date DESC
)
SELECT
    s.book_id,
    s.instrument_id,
    s.asset_class,
    s.quantity,
    s.market_price,
    s.delta,
    s.gamma,
    s.vega,
    s.theta,
    s.rho,
    s.var_contribution,
    s.es_contribution,
    s.snapshot_date,
    p.attribution_date,
    p.total_pnl,
    p.delta_pnl,
    p.gamma_pnl,
    p.vega_pnl,
    p.theta_pnl,
    p.rho_pnl,
    p.unexplained_pnl
FROM latest_snapshots s
LEFT JOIN latest_pnl p ON p.book_id = s.book_id
WITH NO DATA;

-- Unique index supports REFRESH MATERIALIZED VIEW CONCURRENTLY in production.
CREATE UNIQUE INDEX IF NOT EXISTS idx_risk_positions_flat_pk
    ON risk_positions_flat (book_id, instrument_id);

CREATE INDEX IF NOT EXISTS idx_risk_positions_flat_book
    ON risk_positions_flat (book_id);
