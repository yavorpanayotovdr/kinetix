CREATE TABLE historical_scenario_periods (
    period_id           VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    start_date          VARCHAR(10)  NOT NULL,
    end_date            VARCHAR(10)  NOT NULL,
    asset_class_focus   VARCHAR(100),
    severity_label      VARCHAR(50),
    CONSTRAINT pk_historical_scenario_periods PRIMARY KEY (period_id)
);

CREATE TABLE historical_scenario_returns (
    period_id       VARCHAR(255)   NOT NULL REFERENCES historical_scenario_periods(period_id),
    instrument_id   VARCHAR(255)   NOT NULL,
    return_date     VARCHAR(10)    NOT NULL,
    daily_return    NUMERIC(18, 8) NOT NULL,
    source          VARCHAR(100)   NOT NULL DEFAULT 'HISTORICAL',
    CONSTRAINT pk_historical_scenario_returns PRIMARY KEY (period_id, instrument_id, return_date)
);

CREATE INDEX idx_hsr_period_date ON historical_scenario_returns (period_id, return_date);
CREATE INDEX idx_hsr_instrument ON historical_scenario_returns (instrument_id);
