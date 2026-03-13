# Kinetix Platform Memory

## Plan Progress Tracker
Source: `docs/evolution/agent-team-system-plan-no-sre.md` (44 items, 6 phases)

### Phase 1: Critical Fixes -- COMPLETED
- [x] 1.1 Fix Rho Greek (risk-engine: greeks.py, portfolio_risk.py)
- [x] 1.2 Make VaR Deterministic (risk-engine: server.py, valuation.py)
- [x] 1.3 Hash-Chained Audit Trail (audit-service: V2 migration, AuditHasher, verify endpoint)
- [x] 1.4 Extend Audit Coverage (audit-service: V3 migration, userId/userRole/eventType)
- [x] 1.5 Decouple Risk-Orchestrator (risk-orchestrator: HttpPositionServiceClient, removed position-service dep)
- [x] 1.6 Kafka DLQ and Retry (common: RetryableConsumer, updated all 6 consumers)
- [x] Fix: end2end test adapter for PositionServiceClient

### Phase 2: Trading Workflow Essentials -- COMPLETED
- [x] 2.1 Trade Amend and Cancel (position-service: TradeType, TradeStatus, TradeLifecycleService, PUT/DELETE endpoints)
- [x] 2.2 Trade Blotter UI (ui: TradeBlotter component + trades tab, gateway: trade history endpoint)
- [x] 2.3 Pre-Trade Limit Checks (position-service: LimitCheckService, position/notional/concentration limits)
- [x] 2.4 Realized P&L Tracking (common: Position.realizedPnl, position-service: applyTrade computes realized)
- [x] 2.5 Multi-Currency Portfolio Aggregation (position-service: PortfolioAggregationService, FxRateProvider, ui: PortfolioSummaryCard)

### Phase 3: Quantitative Models -- COMPLETED
- [x] 3.1 Black-Scholes Options Pricing (risk-engine: bs_price/delta/gamma/vega/theta/rho, OptionPosition model)
- [x] 3.2 Wire Up Vol Surface and Yield Curve (risk-engine: VolSurface/YieldCurveData models, market_data_consumer wiring)
- [x] 3.3 Fix Historical VaR (risk-engine: historical_returns param for actual return replay)
- [x] 3.4 EWMA Volatility Estimation (risk-engine: ewma.py, VolatilityProvider.ewma() factory, lambda=0.94)
- [x] 3.5 VaR Backtesting Framework (risk-engine: Kupiec POF + Christoffersen tests, regulatory-service: backtest endpoints)
- [x] 3.6 Enhanced DRC with Credit Quality Bucketing (risk-engine: 21 credit ratings, seniority LGD, maturity weights, sector concentration)

### Phase 4: Data Hardening -- COMPLETED
- [x] 4.1 TimescaleDB Retention/Compression (prices: 2yr retention, valuation_jobs: 1yr, audit: 7yr)
- [x] 4.2 Continuous Aggregates (hourly VaR summary, daily P&L summary)
- [x] 4.3 Redis for Shared Caching (VaRCache interface, RedisVaRCache with Lettuce, InMemory fallback)
- [x] 4.4 Correlation ID Propagation (correlationId on TradeEvent/PriceEvent/RiskResultEvent, UUID at source)
- [x] 4.5 Provision All Kafka Topics (expanded create-topics.sh from 3 to 16 topics including DLQs)
- [x] 4.6 Fix Audit Schema Types (V5 migration: quantity/price_amount->NUMERIC, traded_at->TIMESTAMPTZ)
- [x] 4.7 Add Missing Database Indexes (positions: instrument_id/updated_at, daily_risk_snapshots: snapshot_date)
- Note: TimescaleDB migrations require postgres with timescaledb extension; test containers need update

### Phase 5: UI/UX Improvements -- COMPLETED
- [x] 5.1 Accessibility (WAI-ARIA roles, keyboard nav, aria-labels, aria-live)
- [x] 5.2 WebSocket Auto-Reconnect (exponential backoff, max 20 attempts, reconnecting banner)
- [x] 5.3 Position Grid Pagination (50 rows/page)
- [x] 5.4 Column Visibility Toggles (gear dropdown, localStorage persistence)
- [x] 5.5 Consolidate Currency Formatting (shared formatCurrency utility)
- [x] 5.6 Dark Mode (useTheme hook, Tailwind class-based, sun/moon toggle)
- [x] 5.7 Confirmation on Alert Rule Deletion (ConfirmDialog component)
- [x] 5.8 CSV Export from All Tabs (exportToCsv utility, buttons on positions/risk/P&L/alerts)
- [x] Fix: audit-service TimescaleDB compatibility (triggers instead of rules, timescaledb test container)

### Phase 6: Advanced Capabilities -- COMPLETED
- [x] 6.1 Cross-Greeks (Vanna, Volga, Charm -- analytical Black-Scholes formulas)
- [x] 6.2 Variance Reduction for Monte Carlo (antithetic variates)
- [x] 6.3 Dynamic Correlation Estimation (Ledoit-Wolf shrinkage)
- [x] 6.4 Model Governance Framework (regulatory-service: model versioning, approval workflow)
- [x] 6.5 Regulatory Submission Workflow (four-eyes principle, preparer/approver separation)
- [x] 6.6 Limit Management Hierarchy (FIRM→DESK→TRADER→COUNTERPARTY, intraday/overnight)
- [x] 6.7 Margin Calculator (SPAN/SIMM simplified margin by asset class)
- [x] 6.8 Counterparty Risk View (exposure aggregation, netting sets)
- [x] 6.9 Multi-Portfolio Aggregate View (UI: multi-select portfolio picker)
- [x] 6.10 Stress Testing Governance (scenario management, approval workflow)
- [x] 6.11 Data Quality Monitoring (UI: traffic light indicator, staleness detection)
- [x] 6.12 Workspace Customisation (UI: layout/preference persistence via localStorage)

## Test Gap Remediation Progress
Source: `docs/test-gap-remediation-plan.md`

### Phase 1: Critical -- COMPLETED
- [x] RiskResultSchemaCompatibilityTest + notification-service type fix
- [x] TradeEventSchemaCompatibilityTest + risk-orchestrator field fix
- [x] PriceEventSchemaCompatibilityTest
- [x] Position-service acceptance tests (booking, lifecycle, limits) - 11 tests
- [x] Audit-service acceptance tests (hash chain, event consumption) - 7 tests
- [x] buf lint/breaking CI for proto module

### Phase 2: Important -- COMPLETED
- [x] Risk-engine pytest markers (unit/integration/performance)
- [x] Price-service acceptance tests (7 tests, fixed DESC sort order bug)
- [x] Data service contract tests (rates: 6, correlation: 3, volatility: 3, reference-data: 6)
- [x] RetryableConsumer Kafka integration test (3 tests, in position-service due to common module Docker incompatibility)

### Phase 3: Hardening -- COMPLETED
- [x] Playwright P1 browser tests (dark mode 7, CSV export 8, WebSocket reconnect 6)
- [x] Playwright P2 browser tests (column visibility 3, pagination 6, alert rules 5, keyboard nav 8)
- [x] Kafka event schema consolidation into common module (8 per-service files deleted)
- [x] CircuitBreaker HTTP integration test (5 tests, embedded HttpServer)
- [x] gRPC contract integration test (5 tests, real Python risk-engine in Docker)

### Known Issues
- Testcontainers Docker connectivity fails in common module (library module classpath missing Docker client deps). Workaround: place integration tests in service modules.
- Exposed 0.58.0 + Kotest: exceptions inside newSuspendedTransaction cannot be caught by shouldThrow. Workaround: move validation before transactional.run{} block.
- risk-engine Dockerfile needs PYTHONPATH=/app/src env var for module resolution (uv sync doesn't install project in editable mode)

## Workflow Preferences
- [Commit granularity](feedback_commit_granularity.md) — smaller, incremental commits per logical layer

## Key Patterns
- Kotlin services: Ktor, Exposed ORM, Kotest FunSpec, MockK
- Python risk-engine: uv, pytest
- UI: Vite + React + TypeScript + Vitest
- HTTP clients in risk-orchestrator follow pattern: interface + HttpClient impl + DTOs with toDomain() + MockEngine tests
- Kafka consumers now use RetryableConsumer from common module
- Audit events have hash chain (AuditHasher) and immutability rules
