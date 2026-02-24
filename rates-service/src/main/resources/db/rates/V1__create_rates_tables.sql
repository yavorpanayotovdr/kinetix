CREATE TABLE yield_curves (
    curve_id    VARCHAR(255)   NOT NULL,
    as_of_date  TIMESTAMPTZ    NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    source      VARCHAR(50)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_yield_curves PRIMARY KEY (curve_id, as_of_date)
);

CREATE INDEX idx_yield_curves_curve_id ON yield_curves (curve_id, as_of_date DESC);

CREATE TABLE yield_curve_tenors (
    curve_id    VARCHAR(255)   NOT NULL,
    as_of_date  TIMESTAMPTZ    NOT NULL,
    label       VARCHAR(50)    NOT NULL,
    days        INT            NOT NULL,
    rate        NUMERIC(28,12) NOT NULL,

    CONSTRAINT pk_yield_curve_tenors PRIMARY KEY (curve_id, as_of_date, label),
    CONSTRAINT fk_yield_curve_tenors_curve
        FOREIGN KEY (curve_id, as_of_date)
        REFERENCES yield_curves (curve_id, as_of_date)
        ON DELETE CASCADE
);

CREATE TABLE risk_free_rates (
    currency    VARCHAR(3)     NOT NULL,
    tenor       VARCHAR(50)    NOT NULL,
    as_of_date  TIMESTAMPTZ    NOT NULL,
    rate        NUMERIC(28,12) NOT NULL,
    source      VARCHAR(50)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_risk_free_rates PRIMARY KEY (currency, tenor, as_of_date)
);

CREATE INDEX idx_risk_free_rates_lookup ON risk_free_rates (currency, tenor, as_of_date DESC);

CREATE TABLE forward_curves (
    instrument_id VARCHAR(255) NOT NULL,
    as_of_date    TIMESTAMPTZ  NOT NULL,
    asset_class   VARCHAR(50)  NOT NULL,
    source        VARCHAR(50)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_forward_curves PRIMARY KEY (instrument_id, as_of_date)
);

CREATE INDEX idx_forward_curves_instrument ON forward_curves (instrument_id, as_of_date DESC);

CREATE TABLE forward_curve_points (
    instrument_id VARCHAR(255)   NOT NULL,
    as_of_date    TIMESTAMPTZ    NOT NULL,
    tenor         VARCHAR(50)    NOT NULL,
    value         NUMERIC(28,12) NOT NULL,

    CONSTRAINT pk_forward_curve_points PRIMARY KEY (instrument_id, as_of_date, tenor),
    CONSTRAINT fk_forward_curve_points_curve
        FOREIGN KEY (instrument_id, as_of_date)
        REFERENCES forward_curves (instrument_id, as_of_date)
        ON DELETE CASCADE
);
