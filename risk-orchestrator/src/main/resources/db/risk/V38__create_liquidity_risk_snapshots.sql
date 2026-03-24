-- Stores liquidity risk calculation results per book per calculation run.
-- Enables historical querying of LVaR trends, data completeness tracking,
-- and portfolio concentration status over time.
--
-- portfolio_lvar: Liquidity-adjusted VaR using Basel sqrt(T) scaling
-- data_completeness: fraction of portfolio with ADV data (0.0–1.0)
-- portfolio_concentration_status: worst concentration status across positions
CREATE TABLE liquidity_risk_snapshots (
    snapshot_id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    book_id                         VARCHAR(255)  NOT NULL,
    calculated_at                   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    portfolio_lvar                  NUMERIC(24,6) NOT NULL,
    data_completeness               NUMERIC(5,4)  NOT NULL DEFAULT 0,
    portfolio_concentration_status  VARCHAR(20)   NOT NULL DEFAULT 'OK',
    position_risks_json             JSONB         NOT NULL DEFAULT '[]',
    CONSTRAINT pk_liquidity_risk_snapshots PRIMARY KEY (snapshot_id)
);

CREATE INDEX idx_liquidity_risk_snapshots_book ON liquidity_risk_snapshots (book_id, calculated_at DESC);
