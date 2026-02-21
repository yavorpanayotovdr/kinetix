# Kinetix — Build Plan

Modern risk management platform for large financial institutions.

---

## Increment 0: Project Skeleton

### 0.1 Gradle Monorepo Bootstrap
- [x] Initialize git repository
- [x] Create `gradle/wrapper` (Gradle 9.3.1 — upgraded from 8.12 for Java 25 compatibility)
- [x] Create `gradle/libs.versions.toml` (centralized version catalog)
- [x] Create root `settings.gradle.kts` (multi-module includes)
- [x] Create root `build.gradle.kts`
- [x] Create `gradle.properties` (JVM args, Kotlin daemon settings)

### 0.2 Build Logic (Convention Plugins)
- [x] Create `build-logic/settings.gradle.kts`
- [x] Create `build-logic/convention/build.gradle.kts`
- [x] Create `kinetix.kotlin-common.gradle.kts` (compiler opts, JVM toolchain 21)
- [x] Create `kinetix.kotlin-library.gradle.kts` (shared library config)
- [x] Create `kinetix.kotlin-service.gradle.kts` (Ktor application config)
- [x] Create `kinetix.kotlin-testing.gradle.kts` (test deps, task separation)
- [x] Create `kinetix.protobuf.gradle.kts` (proto/gRPC code generation)

### 0.3 Shared Modules
- [x] Create `proto/` module with initial `.proto` files (common types, risk calculation)
- [x] Create `common/` module with domain primitives (`Money`, `PortfolioId`, `AssetClass`)
- [x] First unit tests in `common/` (TDD: red-green-refactor)

### 0.4 Service Scaffolds (Kotlin)
- [x] Scaffold `gateway/` (Ktor Application.kt, /health endpoint, first test)
- [x] Scaffold `position-service/`
- [x] Scaffold `market-data-service/`
- [x] Scaffold `risk-orchestrator/`
- [x] Scaffold `regulatory-service/`
- [x] Scaffold `notification-service/`
- [x] Scaffold `audit-service/`

### 0.5 Python Risk Engine Scaffold
- [x] Create `risk-engine/pyproject.toml` (uv, PEP 621)
- [x] Create `risk-engine/src/kinetix_risk/` package structure
- [x] Create `risk-engine/tests/` with a trivial passing test
- [x] Verify `uv run pytest` passes

### 0.6 UI Scaffold
- [x] Create `ui/` with Vite + React 19 + TypeScript + Tailwind
- [x] Add Vitest config with a trivial passing test
- [x] Verify `npm run test` and `npm run build` pass

### 0.7 Infrastructure
- [x] Create `infra/docker-compose.infra.yml` (PostgreSQL+TimescaleDB, Kafka KRaft, Redis)
- [x] Create `infra/db/init/01-create-databases.sql` (per-service databases)
- [x] Create `infra/kafka/create-topics.sh`
- [x] Verify `docker compose -f infra/docker-compose.infra.yml up` starts clean

### 0.8 Developer Workflow
- [x] Create `.githooks/pre-commit` (unit tests + lint)
- [x] Create `.gitignore`
- [x] Create `.editorconfig`
- [x] Configure git to use `.githooks/` directory
- [x] Verify `./gradlew build` passes end-to-end

---

## Increment 1: Position Management

### 1.1 Position Domain Model
- [x] TDD: Write failing tests for `Trade`, `Position`, `Portfolio` domain objects in `common/`
- [x] Implement domain objects to pass tests
- [x] TDD: Write failing tests for P&L calculation logic
- [x] Implement P&L calculation

### 1.2 Position Service — Persistence
- [x] Create Flyway migrations for `trade_events` and `positions` tables
- [x] TDD: Write failing integration test (Testcontainers + PostgreSQL) for trade event persistence
- [x] Implement `TradeEventRepository` with Exposed
- [x] TDD: Write failing integration test for position projection
- [x] Implement `PositionRepository`

### 1.3 Position Service — Trade Booking
- [x] TDD: Write failing unit tests for `BookTradeCommand` handler
- [x] Implement command handler (creates trade event, updates position projection)
- [x] TDD: Write failing unit tests for `GetPositionsQuery` handler
- [x] Implement query handler

### 1.4 Position Service — Kafka Publishing
- [x] TDD: Write failing integration test (Testcontainers + Kafka) for trade event publishing
- [x] Implement Kafka producer for `trades.lifecycle` topic

### 1.5 Gateway — Position REST Endpoints
- [x] TDD: Write failing route tests for `GET /api/v1/portfolios`
- [x] Implement portfolio routes
- [x] TDD: Write failing route tests for `POST /api/v1/portfolios/{id}/trades`
- [x] Implement trade booking route (calls position-service via in-process client interface)
- [x] TDD: Write failing route tests for `GET /api/v1/portfolios/{id}/positions`
- [x] Implement position query route

### 1.6 Audit Service — Trade Audit Trail
- [x] TDD: Write failing integration test for Kafka consumer + append-only persistence
- [x] Implement audit event consumer and repository
- [x] TDD: Write failing route tests for `GET /api/v1/audit/events`
- [x] Implement audit query route

### 1.7 Acceptance Test
- [x] Write BDD acceptance test (Kotest BehaviorSpec): "Given empty portfolio, When buy trade booked, Then position exists AND audit event recorded"

---

## Increment 2: Market Data Pipeline

### 2.1 Market Data Domain
- [x] TDD: Domain objects for `MarketDataPoint`, `YieldCurve`, `VolSurface`

### 2.2 Market Data Service — Ingestion & Storage
- [x] Flyway migration for `market_data` TimescaleDB hypertable
- [x] TDD: Simulated market data feed generator
- [x] TDD: TimescaleDB repository (Testcontainers)
- [x] TDD: Redis cache for latest prices
- [x] TDD: Kafka publisher for `market.data.prices`

### 2.3 Position Service — P&L Updates
- [x] TDD: Kafka consumer for market data, mark-to-market P&L recalculation

### 2.4 Gateway — WebSocket + Market Data REST
- [x] TDD: WebSocket handler for real-time price streaming
- [x] TDD: REST endpoints for market data queries

### 2.5 UI — Basic Position Grid
- [x] Position table component with live P&L updates via WebSocket
- [x] Vitest unit tests for components (42 tests across 5 files)

### 2.6 Acceptance Test
- [x] "When price update arrives for AAPL, position P&L recalculated within 2 seconds"

---

## Increment 3: Core Risk Engine

### 3.1 Python Risk Engine — VaR
- [x] TDD: Historical VaR calculation (pytest)
- [x] TDD: Parametric VaR calculation
- [x] TDD: Monte Carlo VaR calculation
- [x] TDD: Expected Shortfall
- [x] gRPC server implementation

### 3.2 Risk Orchestrator
- [x] TDD: VaR workflow (fetches positions, calls risk-engine, publishes results)
- [x] TDD: On-demand and scheduled calculation triggers
- [x] Kafka consumer for positions + market data

### 3.3 Gateway — Risk REST Endpoints
- [x] TDD: `GET/POST /api/v1/risk/var/{portfolioId}`

### 3.4 UI — VaR Dashboard
- [x] VaR gauge, time series chart, component breakdown
- [x] Vitest tests

### 3.5 Performance Test
- [x] Monte Carlo VaR: 10K positions, 10K sims < 60s

### 3.6 Acceptance Test
- [x] "When VaR requested for portfolio, receive valid VaR with component breakdown"

---

## Increment 4: Observability

### 4.1 Instrumentation
- [x] Micrometer + Prometheus in all Kotlin services (convention plugin bundle)
- [x] Prometheus `/metrics` endpoint on every Kotlin service (7 services, TDD)
- [x] Business metrics in risk-orchestrator (var.calculation.duration, var.calculation.count, TDD)
- [x] Prometheus metrics in Python risk-engine (histogram, counter, gauge, TDD)

### 4.2 Infrastructure
- [x] Add OTel Collector, Prometheus, Grafana, Loki, Tempo to docker-compose
- [x] Prometheus scrape configs for all 8 services (7 Kotlin + 1 Python)
- [x] Grafana datasource provisioning (Prometheus, Loki, Tempo)

### 4.3 Dashboards
- [x] Risk Overview dashboard (VaR gauge, trend, ES stat, component breakdown, calc count)
- [x] System Health dashboard (request rate, error rate, p95 latency, JVM memory, GC, Kafka lag)
- [x] Market Data dashboard (feed update rate, latency, staleness, updates by instrument)

### 4.4 Alerting
- [x] VaR breach alert rule (VaRBreached)
- [x] Risk calculation slow alert rule (RiskCalculationSlow)
- [x] Market data stale alert rule (MarketDataStale)
- [x] Kafka consumer lag alert rule (KafkaConsumerLag)
- [x] Promtool test file for deterministic alert validation

### 4.5 Acceptance Test
- [x] "When VaR calculation exceeds 30s, duration metric recorded in Prometheus format"

---

## Increment 5: ML Models

### 5.1 Volatility Predictor
- [x] TDD: LSTM model training pipeline (PyTorch)
- [x] TDD: Inference endpoint via gRPC
- [x] Model artifact storage + versioning
- [x] VolatilityProvider abstraction to decouple VaR from static volatility

### 5.2 Credit Default Model
- [x] TDD: Gradient Boosted Trees training (scikit-learn)
- [x] TDD: gRPC scoring endpoint

### 5.3 Anomaly Detector
- [x] TDD: Isolation Forest for risk metric anomalies
- [x] Integration with notification-service (Kafka consumer stub)
- [x] ML prediction metrics (Prometheus histogram, counter)

### 5.4 Performance Test
- [x] Vol prediction for 1000 instruments < 5s (direct tensor + gRPC batch)

### 5.5 Acceptance Test
- [x] BDD acceptance test: vol prediction, credit scoring, anomaly detection

---

## Increment 6: Stress Testing & Greeks

### 6.1 Stress Test Framework
- [x] TDD: Historical stress scenarios (GFC 2008, COVID 2020, Taper Tantrum 2013, Euro Crisis 2011)
- [x] TDD: Hypothetical scenario builder
- [x] Stress test engine with correlation matrix override
- [x] Risk-engine stress test gRPC endpoint

### 6.2 Greeks Calculation
- [x] TDD: Shock-and-revalue Greeks (Delta, Gamma, Vega, Theta, Rho)
- [x] gRPC endpoint for Greeks
- [x] Prometheus metrics (stress_test_duration, greeks_calculation_duration)

### 6.3 Gateway & UI
- [x] REST routes: POST /stress/{portfolioId}, GET /stress/scenarios, POST /greeks/{portfolioId}
- [x] Stress test panel: scenario selector, results table, asset class impact chart
- [x] Greeks heatmap, theta/rho summary, what-if vol bump slider

### 6.4 Acceptance Test
- [x] "When 2008 crisis stress test runs, see losses broken down by asset class"
- [x] "When Greeks calculated, Delta/Gamma/Vega/Theta/Rho computed per asset class"

---

## Increment 7: Regulatory Reporting

### 7.1 FRTB Standardized Approach
- [x] TDD: Sensitivities-Based Method (SbM) — risk weights, delta/vega/curvature charges, correlation scenarios
- [x] TDD: Default Risk Charge (DRC) — credit-sensitive positions, LGD, hedge benefit
- [x] TDD: Residual Risk Add-On (RRAO) — exotic (1%) and other (0.1%) notional charges

### 7.2 Report Generation
- [x] CSV output format (header, 7 risk class rows, DRC, RRAO, total)
- [x] XBRL (simplified XML) output format (FRTBReport root, SbM/DRC/RRAO sections)

### 7.3 gRPC + Gateway
- [x] Proto definition: FrtbRequest/Response, GenerateReportRequest/Response, RegulatoryReportingService
- [x] Python gRPC servicer with Prometheus metrics (frtb_calculation_duration, regulatory_report_total)
- [x] Gateway REST routes: POST /regulatory/frtb/{portfolioId}, POST /regulatory/report/{portfolioId}

### 7.4 UI — Regulatory Dashboard
- [x] Capital requirements display (stat cards: Total, SbM, DRC, RRAO)
- [x] SbM breakdown table (7 risk classes × Delta/Vega/Curvature/Total)
- [x] Report download buttons (CSV, XBRL)

### 7.5 Acceptance Test
- [x] "When FRTB report generated, capital requirement calculated across all seven risk classes"
- [x] "SbM charge positive for equity and commodity risk classes"
- [x] "DRC charge applies to fixed income, RRAO to derivatives"
- [x] "CSV and XBRL reports contain expected structure"

---

## Increment 8: Notifications & Alerting

### 8.1 Notification Service
- [x] TDD: Alert domain models (AlertRule, AlertEvent, AlertType, Severity, DeliveryChannel)
- [x] TDD: Rules engine for VaR breaches, P&L thresholds, risk limits
- [x] TDD: Delivery channels (in-app, email stub, webhook stub) with routing
- [x] Kafka consumer for risk results with rules evaluation and alert delivery

### 8.2 Gateway & UI
- [x] Gateway REST routes for alert rules CRUD and recent alerts
- [x] Notification center UI with alert rules configuration and recent alerts

### 8.3 Acceptance Test
- [x] "When VaR exceeds limit, alert generated with CRITICAL severity and delivered to in-app and email"
- [x] "Disabled rules do not produce alerts; only matching rules fire"

---

## Increment 9: Production Hardening

### 9.1 Security
- [x] JWT authentication with Keycloak (docker-compose)
- [x] RBAC roles: ADMIN, TRADER, RISK_MANAGER, COMPLIANCE, VIEWER
- [x] TLS for gRPC and HTTP

### 9.2 Resilience
- [x] Circuit breakers for inter-service calls
- [x] Rate limiting on gateway
- [x] Connection pool tuning (HikariCP)

### 9.3 Performance
- [x] Gatling load test suite
- [x] p95 < 3s for API calls, > 99% success rate under load
