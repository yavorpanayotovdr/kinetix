---
name: kinetix-architecture
description: Kinetix platform architecture -- services, communication patterns, infrastructure, and conventions. Use this context when answering questions about the system design, choosing where to place code, or understanding service interactions.
user-invocable: false
---

# Kinetix Architecture Reference

## Service Inventory

| Service | Port | Language | Role | Database |
|---|---|---|---|---|
| Gateway | 8080 | Kotlin/Ktor | API routing, JWT auth, WebSocket proxy | -- |
| Position Service | 8081 | Kotlin/Ktor | Trade booking, position calculation, event sourcing | PostgreSQL `kinetix_position` |
| Price Service | 8082 | Kotlin/Ktor | Price ingestion, Redis caching, Kafka publishing | PostgreSQL `kinetix_price` (TimescaleDB) |
| Risk Orchestrator | 8083 | Kotlin/Ktor | Coordinates VaR/risk calculations via gRPC | PostgreSQL `kinetix_risk` |
| Rates Service | 8084 | Kotlin/Ktor | Yield curves, forward curves, risk-free rates | PostgreSQL `kinetix_rates` |
| Audit Service | 8084 | Kotlin/Ktor | Immutable audit log, consumes trade events | PostgreSQL `kinetix_audit` |
| Regulatory Service | 8085 | Kotlin/Ktor | FRTB regulatory reporting | PostgreSQL `kinetix_regulatory` |
| Notification Service | 8086 | Kotlin/Ktor | Risk breach alerts, anomaly notifications | PostgreSQL `kinetix_notification` |
| Risk Engine | 50051 | Python/gRPC | VaR (Historical/Parametric/Monte Carlo), Greeks, stress testing, ML models | -- (in-memory) |
| UI | 5173 | TypeScript/React | Trading and risk dashboard (Vite, Tailwind CSS) | -- |

## Gradle Modules

```
proto/                  Protobuf definitions and generated code
common/                 Shared Kotlin library
gateway/
position-service/
price-service/
rates-service/
risk-orchestrator/
regulatory-service/
notification-service/
audit-service/
acceptance-tests/       End-to-end tests
```

Convention plugins in `build-logic/convention/`:
- `kinetix.kotlin-common` -- Kotlin JVM 21, strict compiler flags
- `kinetix.kotlin-service` -- Ktor server, OpenTelemetry logging, test host
- `kinetix.kotlin-library` -- Shared library setup
- `kinetix.kotlin-testing` -- Kotest, MockK, Testcontainers
- `kinetix.protobuf` -- Protobuf compilation

## Kafka Topics

| Topic | Producers | Consumers |
|---|---|---|
| `trades.lifecycle` | Position Service | Risk Orchestrator, Audit Service, Notification Service |
| `price.updates` | Price Service | Risk Orchestrator, Position Service, Gateway |
| `risk.results` | Risk Orchestrator | Notification Service, Regulatory Service |
| `rates.yield-curves` | Rates Service | Risk Orchestrator |
| `rates.risk-free` | Rates Service | Risk Orchestrator |
| `rates.forwards` | Rates Service | Risk Orchestrator |

3 partitions per topic, JSON serialization via kotlinx.serialization, at-least-once delivery.

## gRPC Services (proto/)

**RiskCalculationService** -- VaR calculation (Historical, Parametric, Monte Carlo)
```
CalculateVaR(VaRRequest) -> VaRResponse
CalculateVaRStream(stream VaRRequest) -> stream VaRResponse
```

**StressTestService** -- Stress testing and Greeks
```
RunStressTest(StressTestRequest) -> StressTestResponse
ListScenarios(ListScenariosRequest) -> ListScenariosResponse
CalculateGreeks(GreeksRequest) -> GreeksResponse
```

**RegulatoryReportingService** -- FRTB reporting (CSV, XBRL)
```
CalculateFrtb(FrtbRequest) -> FrtbResponse
GenerateReport(GenerateReportRequest) -> GenerateReportResponse
```

**MLPredictionService** -- Volatility, credit scoring, anomaly detection
```
PredictVolatility / PredictVolatilityBatch
ScoreCredit / DetectAnomaly
```

**MarketDataDependenciesService** -- Discover required market data for a calculation
```
DiscoverDependencies(DataDependenciesRequest) -> DataDependenciesResponse
```

Common types: Money, PortfolioId, TradeId, InstrumentId, Position, AssetClass (EQUITY, FIXED_INCOME, FX, COMMODITY, DERIVATIVE).

## Communication Patterns

- **HTTP/REST** -- Gateway routes requests to backend services (CIO engine). kotlinx.serialization for JSON.
- **gRPC** -- Risk Orchestrator calls Risk Engine over TCP:50051. Supports streaming for batch VaR.
- **Kafka** -- Event-driven: Position Service publishes trade events; Price Service publishes price updates; Risk Orchestrator publishes risk results.
- **WebSocket** -- Gateway exposes `/ws/prices` for real-time price broadcasting to the UI via `PriceBroadcaster`.

## Auth (Keycloak)

Realm: `kinetix` on port 8180. Client: `kinetix-api` (public, OIDC).

Roles: ADMIN, TRADER, RISK_MANAGER, COMPLIANCE, VIEWER.

Gateway validates JWTs and enforces permissions per route (`requirePermission()`). Key permissions: READ_PORTFOLIOS, WRITE_TRADES, READ_POSITIONS, CALCULATE_RISK, READ_RISK, READ_REGULATORY, READ_ALERTS.

Default users: admin/admin, trader1/trader1, risk_mgr/risk_mgr, compliance1/compliance1, viewer1/viewer1.

## Observability

- **OpenTelemetry Collector** (4317 gRPC, 4318 HTTP) receives traces, logs, metrics from all services.
- **Prometheus** (9090) scrapes `/metrics` from every service at 15s intervals.
- **Loki** (3100) for log aggregation.
- **Tempo** (3200) for distributed tracing.
- **Grafana** (3000) dashboards with Prometheus, Loki, Tempo data sources.
- Services use Micrometer (Prometheus registry) and OTEL structured logging.
- Risk Engine exposes custom Prometheus metrics on port 9091.

## Infrastructure

- **PostgreSQL 17** (TimescaleDB) -- database per service (8 databases total).
- **Redis 7** -- price caching layer.
- **Kafka 3.9** (KRaft mode, no Zookeeper).
- Docker Compose files: `infra`, `auth`, `observability` (in `infra/`).
- Helm chart in `deploy/helm/kinetix/` with Bitnami dependencies.

## Build & Test

- Kotlin 2.1.20 / Java 21 / Gradle 9.3 (Kotlin DSL)
- Python 3.12 with `uv` package manager
- Node.js 22 with npm
- `./gradlew test` -- unit tests only
- `./gradlew integrationTest` -- integration tests (Testcontainers)
- `./gradlew end2EndTest` -- end-to-end tests
- `cd risk-engine && uv run pytest` -- Python tests
- `cd ui && npm run test` -- UI tests (Vitest)
- `./gradlew :load-tests:gatlingRun` -- load tests
