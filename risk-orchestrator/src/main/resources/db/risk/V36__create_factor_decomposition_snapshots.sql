-- Stores factor risk decomposition results per book per calculation run.
-- Enables historical querying of systematic vs idiosyncratic VaR trends
-- and factor contribution breakdown over time.
--
-- total_var: total portfolio VaR in base currency
-- systematic_var: VaR explained by the 5-factor model
-- idiosyncratic_var: residual VaR not explained by factors (= total - systematic)
-- r_squared: fraction of total variance explained by factor model (0.0–1.0)
-- concentration_warning: true when any single factor exceeds 60% of total VaR
-- factors_json: per-factor contributions array (type, var, pct, loading, method)
CREATE TABLE factor_decomposition_snapshots (
    snapshot_id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    book_id               VARCHAR(255)  NOT NULL,
    calculated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    total_var             NUMERIC(24,6) NOT NULL,
    systematic_var        NUMERIC(24,6) NOT NULL,
    idiosyncratic_var     NUMERIC(24,6) NOT NULL,
    r_squared             NUMERIC(8,6)  NOT NULL,
    concentration_warning BOOLEAN       NOT NULL DEFAULT FALSE,
    factors_json          JSONB         NOT NULL DEFAULT '[]',
    CONSTRAINT pk_factor_decomposition_snapshots PRIMARY KEY (snapshot_id)
);

CREATE INDEX idx_factor_decomposition_snapshots_book
    ON factor_decomposition_snapshots (book_id, calculated_at DESC);
