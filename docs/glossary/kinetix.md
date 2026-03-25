# Kinetix System Glossary

Terms, patterns, and concepts specific to the Kinetix platform and how it implements financial risk management.

---

## Trade Lifecycle

| Term | Definition |
|------|-----------|
| **Trade Booking** | Initial entry of a trade into the position-service. Creates a position (or updates an existing one) and emits a `TradeEvent` to Kafka. |
| **Trade Status** | `LIVE` (active), `AMENDED` (modified), `CANCELLED` (terminated). Only `LIVE` trades count toward positions and limits. |
| **Trade Type** | `BUY` or `SELL`. Determines how quantity and P&L are applied to the position. |
| **Trade Amend** | `PUT /api/trades/{id}` — modifies a live trade's quantity or price. The original trade is marked `AMENDED`, a new `LIVE` trade is created, and the position is recalculated. |
| **Trade Cancel** | `DELETE /api/trades/{id}` — terminates a live trade. The trade is marked `CANCELLED`, and its contribution to the position (quantity, realised P&L) is reversed. |
| **TradeEvent** | Kafka message on the `trades` topic capturing a trade action. Types: `NEW`, `AMENDED`, `CANCELLED`. Carries a `correlationId` for cross-service tracing. |
| **TradeLifecycleService** | Position-service component responsible for amend and cancel operations, including P&L reversal and position recalculation. |

## Position Model

| Term | Definition |
|------|-----------|
| **Position** | Net holding of an instrument within a book. Aggregated from all `LIVE` trades. Stores quantity, average price, market value, unrealised P&L, and realised P&L. |
| **InstrumentType** | Sealed interface hierarchy in the `common` module with 11 typed subtypes (CashEquity, GovernmentBond, CorporateBond, FxSpot, FxForward, EquityOption, EquityFuture, CommodityFuture, CommodityOption, FxOption, InterestRateSwap). Each subtype has typed attributes (e.g. `OptionAttributes`, `BondAttributes`). |
| **Instrument Master** | Reference-data-service table storing instrument definitions with type-specific attributes in a JSONB column. |
| **Realised P&L** | Computed at trade execution time by `applyTrade()` in position-service. Uses FIFO-style calculation against average entry price. |
| **Position Grid** | UI table on the Positions tab. Supports pagination (50 rows/page), column visibility toggles, and CSV export. |

## Limit Management

| Term | Definition |
|------|-----------|
| **Limit Hierarchy** | `FIRM -> DIVISION -> DESK -> BOOK / TRADER / COUNTERPARTY`. Limits cascade down — a desk limit cannot exceed its parent division limit. |
| **LimitCheckService** | Position-service component that validates pre-trade limits before booking. Checks position limits, notional limits, and concentration limits. |
| **Position Limit** | Maximum quantity of a single instrument. |
| **Notional Limit** | Maximum exposure in monetary terms (quantity * price). |
| **Concentration Limit** | Maximum percentage of portfolio value in a single instrument. |
| **Counterparty Limit** | Maximum aggregate exposure to a single counterparty across all netting sets. |
| **Intraday Limit** | Tighter threshold enforced during trading hours. |
| **Overnight Limit** | Threshold for positions held past end-of-day. |
| **Temporary Limit Increase** | Time-bounded exception with an expiration timestamp. Auto-expires and reverts to the base limit. |
| **Limit Warning** | Triggered when exposure reaches a configurable threshold (default 80%) of the limit. |
| **Limit Breach** | Exposure exceeds the limit. Trade is rejected pre-trade; existing breaches are flagged for remediation. |

## Risk Calculation Pipeline

| Term | Definition |
|------|-----------|
| **Risk-Orchestrator** | Kotlin service that coordinates risk calculations. Fetches positions (via HTTP), enriches them with instrument data, and dispatches to the risk-engine via gRPC. |
| **Risk-Engine** | Python service (`risk-engine/src/kinetix_risk/`) that performs all quantitative calculations: VaR, Greeks, Monte Carlo, stress testing, FRTB capital, counterparty risk. Communicates via gRPC using proto definitions. |
| **Position Resolver** | `position_resolver.py` — converts proto position messages into typed Python position objects (BondPosition, OptionPosition, FuturePosition, etc.) based on instrument type. |
| **Deterministic VaR** | When seed > 0, Monte Carlo produces repeatable results. Seed = 0 is non-deterministic. Controlled via the `seed` field in the gRPC request. |
| **VaR Cache** | Interface (`VaRCache`) with two implementations: `RedisVaRCache` (shared, uses Lettuce client) and `InMemoryVaRCache` (per-instance fallback). Keyed by portfolio + calculation parameters. |
| **RiskResultEvent** | Kafka message on the `risk-results` topic. Consumed by notification-service for WebSocket push and by position-service for snapshot storage. |

## Event Architecture

| Term | Definition |
|------|-----------|
| **TradeEvent** | Published by position-service when a trade is booked, amended, or cancelled. |
| **PriceEvent** | Published by price-service when new market data is ingested. |
| **RiskResultEvent** | Published by risk-orchestrator after a risk calculation completes. |
| **RetryableConsumer** | Common-module wrapper for Kafka consumers. Provides exponential backoff retry (base delay * 2^attempt) with configurable max retries (default 3) before routing to a DLQ. |
| **DLQ (Dead Letter Queue)** | Dedicated Kafka topic (e.g. `trades.dlq`, `prices.dlq`) receiving messages that failed all retry attempts. Used for investigation and replay. |
| **Correlation ID** | UUID assigned at the source of an event (e.g. trade booking) and propagated through all downstream events and service calls. Enables cross-service tracing. |

## Audit Trail

| Term | Definition |
|------|-----------|
| **Hash Chain** | Each audit event's hash is computed from its own data plus the previous event's hash (SHA-256). This creates a tamper-evident chain — modifying any past event invalidates all subsequent hashes. |
| **AuditHasher** | Audit-service component that computes and verifies hash chains. |
| **Audit Event Types** | `TRADE_BOOKED`, `TRADE_AMENDED`, `TRADE_CANCELLED`, `RISK_CALCULATED`, `LIMIT_BREACHED`, `SCENARIO_APPROVED`, `MODEL_APPROVED`, `SUBMISSION_PREPARED`, `SUBMISSION_APPROVED`. |
| **Verify Endpoint** | `GET /api/audit/verify` — walks the hash chain and reports any integrity violations. |
| **Retention Policy** | Audit data retained for 7 years (regulatory requirement). Prices: 2 years. Valuation jobs: 1 year. Enforced by TimescaleDB retention policies. |

## Model Governance

| Term | Definition |
|------|-----------|
| **Model Version** | Regulatory-service tracks all risk model releases with version, description, approval status, and approval chain. |
| **Model Approval Workflow** | `DRAFT -> PENDING_REVIEW -> APPROVED / REJECTED`. Requires four-eyes principle (preparer != approver). |
| **Regulatory Submission** | `DRAFT -> PREPARED -> SUBMITTED -> ACCEPTED / REJECTED`. The prepare and submit steps must be performed by different users (four-eyes). |
| **Backtest Result** | Stored output of Kupiec POF and Christoffersen tests, linked to a specific model version and time window. |

## Stress Testing

| Term | Definition |
|------|-----------|
| **Scenario Category** | `HISTORICAL` (based on past events like "Equity Crash 2020") or `CUSTOM` (user-defined shocks). |
| **Scenario Approval** | Regulatory scenarios require four-eyes sign-off before use in official reporting. Managed via the regulatory-service. |
| **Stress Limit Breach** | When a stressed risk metric (e.g. stressed VaR) exceeds a scenario-specific threshold. |

## Counterparty Risk (Kinetix-specific)

| Term | Definition |
|------|-----------|
| **Netting Agreement Types** | `ISDA_2002`, `ISDA_1992`, `GMRA` (Global Master Repurchase Agreement). Stored in reference-data-service. |
| **Counterparty Risk View** | UI panel aggregating exposure by counterparty across netting sets, showing PFE, EPE, CVA, and netting benefit. |
| **Wrong-Way Risk Flag** | Heuristic indicator when a counterparty's sector correlates with the exposure direction. |

## Infrastructure

| Term | Definition |
|------|-----------|
| **API Gateway** | Kotlin/Ktor service aggregating backend service calls for the UI. All UI HTTP requests route through the gateway. |
| **Notification Service** | Consumes `risk-results`, `prices`, and `trades` Kafka topics and pushes updates to the UI via WebSocket. |
| **TimescaleDB** | PostgreSQL extension used for time-series tables (prices, valuation jobs, audit events, risk snapshots). Provides automatic partitioning, compression, and retention policies. |
| **Continuous Aggregate** | TimescaleDB materialised view that pre-computes summaries. Kinetix uses hourly VaR summaries and daily P&L summaries. |
| **Flyway Migration** | SQL schema versioning. Migrations run inside PostgreSQL transactions — `CREATE INDEX CONCURRENTLY` and similar transaction-incompatible statements must not be used. |
| **Circuit Breaker** | Three-state pattern (CLOSED -> OPEN -> HALF_OPEN) wrapping HTTP clients. Opens after consecutive failures (default 5), resets after a timeout (default 30s). Prevents cascading failures between services. |
| **WebSocket Auto-Reconnect** | UI reconnection with exponential backoff, max 20 attempts. Displays a "reconnecting" banner during disconnection. |

## UI Concepts

| Term | Definition |
|------|-----------|
| **Dark Mode** | Class-based Tailwind theme toggle. Persisted to localStorage via the `useTheme` hook. |
| **Column Visibility Toggles** | Gear dropdown on the position grid allowing users to show/hide columns. Selection persisted to localStorage. |
| **CSV Export** | Available on all data tabs (positions, risk, P&L, alerts). Uses a shared `exportToCsv` utility. |
| **Workspace Customisation** | Layout and preference persistence via localStorage. Users can configure which panels are visible and their arrangement. |
| **Data Quality Indicator** | Traffic-light indicator showing staleness of market data feeds. Green = fresh, amber = stale, red = disconnected. |
| **Alert Rules** | User-defined thresholds (e.g. "notify if VaR exceeds $1M"). Deletion requires confirmation via ConfirmDialog. |
| **Multi-Portfolio Picker** | UI component allowing selection of multiple portfolios for aggregate risk/P&L views. |

## Kafka Topics

| Topic | Publisher | Consumers |
|-------|-----------|-----------|
| `trades` | position-service | risk-orchestrator, audit-service, notification-service |
| `trades.dlq` | RetryableConsumer | (investigation/replay) |
| `prices` | price-service | risk-orchestrator, notification-service |
| `prices.dlq` | RetryableConsumer | (investigation/replay) |
| `risk-results` | risk-orchestrator | notification-service, position-service |
| `risk-results.dlq` | RetryableConsumer | (investigation/replay) |
| `audit-events` | audit-service | (external consumers) |

## gRPC Contracts

| Service | Proto File | Purpose |
|---------|-----------|---------|
| risk-engine | `proto/src/main/proto/risk_service.proto` | VaR, Greeks, stress testing, FRTB, counterparty risk calculations |

Positions are sent as proto messages enriched with `instrument_type` and type-specific attribute fields, which the Python `position_resolver.py` converts into typed position objects for calculation.
