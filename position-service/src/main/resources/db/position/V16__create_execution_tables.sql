-- V16: Execution management tables for Direction 5 (Order/Execution Integration).
-- These tables are owned by the FIX adapter logic housed in com.kinetix.position.fix
-- until the fix-adapter service is extracted into its own Gradle module.

-- -----------------------------------------------------------------------
-- execution_orders: tracks the full lifecycle of an order from submission
-- through risk check, FIX routing, fills, and terminal state.
-- -----------------------------------------------------------------------
CREATE TABLE execution_orders (
    order_id             VARCHAR(255)   NOT NULL,
    book_id              VARCHAR(255)   NOT NULL,
    instrument_id        VARCHAR(255)   NOT NULL,
    side                 VARCHAR(10)    NOT NULL,
    quantity             NUMERIC(28,12) NOT NULL,
    order_type           VARCHAR(50)    NOT NULL,
    limit_price          NUMERIC(28,12),
    arrival_price        NUMERIC(28,12) NOT NULL,
    submitted_at         TIMESTAMPTZ    NOT NULL,
    status               VARCHAR(30)    NOT NULL,
    risk_check_result    VARCHAR(20),
    risk_check_details   TEXT,
    fix_session_id       VARCHAR(255),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_execution_orders PRIMARY KEY (order_id)
);

CREATE INDEX idx_execution_orders_book_id      ON execution_orders (book_id);
CREATE INDEX idx_execution_orders_status       ON execution_orders (status);
CREATE INDEX idx_execution_orders_submitted_at ON execution_orders (submitted_at);

-- -----------------------------------------------------------------------
-- execution_fills: one row per FIX ExecutionReport fill (ExecType=F or 1).
-- fill_id is deterministically derived from (fix_session_id, fix_exec_id)
-- to guarantee idempotent processing on FIX session reconnect.
-- fix_exec_id has a unique constraint to enforce fill deduplication.
-- -----------------------------------------------------------------------
CREATE TABLE execution_fills (
    fill_id          VARCHAR(255)   NOT NULL,
    order_id         VARCHAR(255)   NOT NULL REFERENCES execution_orders(order_id),
    book_id          VARCHAR(255)   NOT NULL,
    instrument_id    VARCHAR(255)   NOT NULL,
    fill_time        TIMESTAMPTZ    NOT NULL,
    fill_qty         NUMERIC(28,12) NOT NULL,
    fill_price       NUMERIC(28,12) NOT NULL,
    fill_type        VARCHAR(20)    NOT NULL,
    venue            VARCHAR(100),
    cumulative_qty   NUMERIC(28,12) NOT NULL,
    average_price    NUMERIC(28,12) NOT NULL,
    fix_exec_id      VARCHAR(255),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_execution_fills   PRIMARY KEY (fill_id),
    CONSTRAINT uq_execution_fill_exec_id UNIQUE (fix_exec_id)
);

CREATE INDEX idx_execution_fills_order_id  ON execution_fills (order_id);
CREATE INDEX idx_execution_fills_fill_time ON execution_fills (fill_time);
CREATE INDEX idx_execution_fills_book_id   ON execution_fills (book_id);

-- -----------------------------------------------------------------------
-- execution_cost_analysis: computed when an order reaches terminal state.
-- slippage_bps = (avg_fill_price - arrival_price) / arrival_price * 10000 * side_sign
-- -----------------------------------------------------------------------
CREATE TABLE execution_cost_analysis (
    order_id             VARCHAR(255)   NOT NULL,
    book_id              VARCHAR(255)   NOT NULL,
    instrument_id        VARCHAR(255)   NOT NULL,
    completed_at         TIMESTAMPTZ    NOT NULL,
    arrival_price        NUMERIC(28,12) NOT NULL,
    average_fill_price   NUMERIC(28,12) NOT NULL,
    side                 VARCHAR(10)    NOT NULL,
    total_qty            NUMERIC(28,12) NOT NULL,
    slippage_bps         NUMERIC(20,10) NOT NULL,
    market_impact_bps    NUMERIC(20,10),
    timing_cost_bps      NUMERIC(20,10),
    total_cost_bps       NUMERIC(20,10) NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_execution_cost_analysis PRIMARY KEY (order_id)
);

CREATE INDEX idx_eca_book_id      ON execution_cost_analysis (book_id);
CREATE INDEX idx_eca_completed_at ON execution_cost_analysis (completed_at);

-- -----------------------------------------------------------------------
-- prime_broker_reconciliation: daily snapshot of internal vs PB positions.
-- breaks stored as JSONB for flexibility.
-- -----------------------------------------------------------------------
CREATE TABLE prime_broker_reconciliation (
    id                   VARCHAR(255)   NOT NULL,
    reconciliation_date  VARCHAR(10)    NOT NULL,
    book_id              VARCHAR(255)   NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    total_positions      INTEGER        NOT NULL,
    matched_count        INTEGER        NOT NULL,
    break_count          INTEGER        NOT NULL,
    breaks               JSONB          NOT NULL DEFAULT '[]',
    reconciled_at        TIMESTAMPTZ    NOT NULL,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_prime_broker_recon PRIMARY KEY (id),
    CONSTRAINT uq_pb_recon_date_book UNIQUE (reconciliation_date, book_id)
);

CREATE INDEX idx_pb_recon_book_id ON prime_broker_reconciliation (book_id);
CREATE INDEX idx_pb_recon_date    ON prime_broker_reconciliation (reconciliation_date);

-- -----------------------------------------------------------------------
-- fix_sessions: tracks active FIX sessions and their sequence numbers.
-- Managed by the fix-adapter logic; surfaced in SystemDashboard health check.
-- -----------------------------------------------------------------------
CREATE TABLE fix_sessions (
    session_id       VARCHAR(255)   NOT NULL,
    counterparty     VARCHAR(255)   NOT NULL,
    status           VARCHAR(30)    NOT NULL DEFAULT 'DISCONNECTED',
    last_message_at  TIMESTAMPTZ,
    inbound_seq_num  INTEGER        NOT NULL DEFAULT 0,
    outbound_seq_num INTEGER        NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_fix_sessions PRIMARY KEY (session_id)
);
