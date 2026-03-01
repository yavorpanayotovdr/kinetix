CREATE TABLE backtest_results (
    id                         VARCHAR(255)     NOT NULL,
    portfolio_id               VARCHAR(255)     NOT NULL,
    calculation_type           VARCHAR(50)      NOT NULL,
    confidence_level           DOUBLE PRECISION NOT NULL,
    total_days                 INTEGER          NOT NULL,
    violation_count            INTEGER          NOT NULL,
    violation_rate             DOUBLE PRECISION NOT NULL,
    kupiec_statistic           DOUBLE PRECISION NOT NULL,
    kupiec_p_value             DOUBLE PRECISION NOT NULL,
    kupiec_pass                BOOLEAN          NOT NULL,
    christoffersen_statistic   DOUBLE PRECISION NOT NULL,
    christoffersen_p_value     DOUBLE PRECISION NOT NULL,
    christoffersen_pass        BOOLEAN          NOT NULL,
    traffic_light_zone         VARCHAR(10)      NOT NULL,
    calculated_at              TIMESTAMPTZ      NOT NULL,

    CONSTRAINT pk_backtest_results PRIMARY KEY (id)
);

CREATE INDEX idx_backtest_portfolio ON backtest_results (portfolio_id);
CREATE INDEX idx_backtest_calculated_at ON backtest_results (calculated_at DESC);
