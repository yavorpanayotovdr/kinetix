-- SA-CCR (BCBS 279) regulatory EAD results persistence.
-- TimescaleDB hypertable for time-series queries and automatic retention.

CREATE TABLE sa_ccr_results (
    id                BIGSERIAL       NOT NULL,
    counterparty_id   VARCHAR(255)    NOT NULL,
    netting_set_id    VARCHAR(255)    NOT NULL,
    calculated_at     TIMESTAMPTZ     NOT NULL,
    replacement_cost  NUMERIC(24,6)   NOT NULL,
    pfe_addon         NUMERIC(24,6)   NOT NULL,
    multiplier        NUMERIC(10,8)   NOT NULL,
    ead               NUMERIC(24,6)   NOT NULL,
    alpha             NUMERIC(6,4)    NOT NULL DEFAULT 1.4,
    collateral_net    NUMERIC(24,6)   NOT NULL DEFAULT 0,
    position_count    INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_sa_ccr_results PRIMARY KEY (id, calculated_at)
);

SELECT create_hypertable('sa_ccr_results', 'calculated_at',
    chunk_time_interval => INTERVAL '7 days',
    migrate_data => true
);

CREATE INDEX idx_sa_ccr_counterparty_time
    ON sa_ccr_results (counterparty_id, calculated_at DESC);

-- Compress after 90 days, retain for 3 years (regulatory record-keeping)
ALTER TABLE sa_ccr_results
    SET (timescaledb.compress,
         timescaledb.compress_segmentby = 'counterparty_id',
         timescaledb.compress_orderby = 'calculated_at DESC');

SELECT add_compression_policy('sa_ccr_results', INTERVAL '90 days');
SELECT add_retention_policy('sa_ccr_results', INTERVAL '1095 days');
