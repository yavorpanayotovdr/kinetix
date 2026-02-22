CREATE TABLE frtb_calculations (
    id                    VARCHAR(255)     NOT NULL,
    portfolio_id          VARCHAR(255)     NOT NULL,
    total_sbm_charge      DOUBLE PRECISION NOT NULL,
    gross_jtd             DOUBLE PRECISION NOT NULL,
    hedge_benefit         DOUBLE PRECISION NOT NULL,
    net_drc               DOUBLE PRECISION NOT NULL,
    exotic_notional       DOUBLE PRECISION NOT NULL,
    other_notional        DOUBLE PRECISION NOT NULL,
    total_rrao            DOUBLE PRECISION NOT NULL,
    total_capital_charge  DOUBLE PRECISION NOT NULL,
    sbm_charges_json      TEXT             NOT NULL,
    calculated_at         TIMESTAMPTZ      NOT NULL,
    stored_at             TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_frtb_calculations PRIMARY KEY (id)
);

CREATE INDEX idx_frtb_portfolio ON frtb_calculations (portfolio_id);
CREATE INDEX idx_frtb_calculated_at ON frtb_calculations (calculated_at DESC);
