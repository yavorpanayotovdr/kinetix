-- frtb_calculations: rename column and update compression segmentby.
ALTER TABLE frtb_calculations RENAME COLUMN portfolio_id TO book_id;

DROP INDEX IF EXISTS idx_frtb_portfolio;
CREATE INDEX idx_frtb_book ON frtb_calculations (book_id);

ALTER TABLE frtb_calculations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'book_id',
    timescaledb.compress_orderby = 'calculated_at DESC'
);

-- backtest_results: rename column and update compression segmentby.
ALTER TABLE backtest_results RENAME COLUMN portfolio_id TO book_id;

DROP INDEX IF EXISTS idx_backtest_portfolio;
CREATE INDEX idx_backtest_book ON backtest_results (book_id);

ALTER TABLE backtest_results SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'book_id',
    timescaledb.compress_orderby = 'calculated_at DESC'
);

-- stress_test_results: plain table, rename column and index.
ALTER TABLE stress_test_results RENAME COLUMN portfolio_id TO book_id;

DROP INDEX IF EXISTS idx_stress_results_portfolio;
CREATE INDEX idx_stress_results_book ON stress_test_results (book_id, calculated_at DESC);
