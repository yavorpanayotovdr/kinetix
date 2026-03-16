CREATE TABLE instruments (
    instrument_id   VARCHAR(255)   NOT NULL,
    instrument_type VARCHAR(50)    NOT NULL,
    display_name    VARCHAR(255)   NOT NULL,
    asset_class     VARCHAR(50)    NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    attributes      JSONB          NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_instruments PRIMARY KEY (instrument_id)
);

CREATE INDEX idx_instruments_asset_class ON instruments (asset_class);
CREATE INDEX idx_instruments_type ON instruments (instrument_type);
