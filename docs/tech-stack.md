# Kinetix Technology Stack

Kinetix is a real-time portfolio risk management platform built as a polyglot monorepo. Kotlin/Ktor microservices handle trade lifecycle, position management, and market data ingestion; a Python risk engine performs VaR, Monte Carlo, Greeks, and ML-based calculations over gRPC; a React frontend delivers a real-time trading dashboard; and Kafka, PostgreSQL/TimescaleDB, and Redis provide the messaging, persistence, and caching backbone. For architecture decisions see the [ADR index](adr/README.md); for the project roadmap see [plan.md](plan.md).

---

## Architecture at a Glance

```
                          ┌──────────────┐
                          │   React UI   │
                          │  (Vite/TS)   │
                          │  :5173 dev   │
                          └──────┬───────┘
                                 │ HTTP / WebSocket
                          ┌──────▼───────┐
                          │   Gateway    │
                          │  Ktor :8080  │
                          └──┬───┬───┬───┘
               ┌─────────────┘   │   └─────────────┐
               │                 │                  │
        ┌──────▼───────┐ ┌──────▼───────┐ ┌────────▼─────────┐
        │  Position    │ │    Price     │ │ Risk Orchestrator │
        │ Svc :8081    │ │  Svc :8082   │ │    Svc :8083      │
        └──────┬───────┘ └──────┬───────┘ └────────┬──────────┘
               │                │                  │╲
               │                │            gRPC  │ ╲ HTTP
               │                │           ┌──────▼──┐╲
               │                │           │  Risk   │ ╲
               │                │           │  Engine │  ╲
               │                │           │  :50051 │   ╲
               │                │           └─────────┘    ╲
               │                │    ┌──────────────────────▼──────┐
               │                │    │    Market Data Services      │
               │                │    │                              │
               │                │    │  Rates Svc        :8088     │
               │                │    │  Ref Data Svc     :8089     │
               │                │    │  Volatility Svc   :8090     │
               │                │    │  Correlation Svc  :8091     │
               │                │    └──────────────┬──────────────┘
               │                │                   │
        ┌──────▼────────────────▼───────────────────▼──┐
        │              Apache Kafka :9092               │
        │  trades.lifecycle  │  price.updates           │
        │  risk.results      │  rates.*                 │
        │  reference-data.*  │  volatility.surfaces     │
        │  correlation.matrices                         │
        └──┬────────────────────────────────────────┬──┘
           │                                        │
    ┌──────▼───────┐                         ┌──────▼───────┐
    │ Audit Svc    │                         │ Notification │
    │   :8084      │                         │  Svc :8086   │
    └──────────────┘                         └──────────────┘

    ┌──────────────┐
    │ Regulatory   │
    │  Svc :8085   │
    └──────────────┘

    ┌──────────────────────────────────────────────┐
    │              Datastores                       │
    │  PostgreSQL/TimescaleDB :5432                 │
    │  Redis :6379                                  │
    └──────────────────────────────────────────────┘

    ┌──────────────────────────────────────────────┐
    │              Observability                    │
    │  Prometheus :9090  │  Grafana :3000           │
    │  Loki :3100        │  Tempo :3200             │
    │  OTel Collector :4317/:4318                   │
    └──────────────────────────────────────────────┘

    ┌──────────────────────────────────────────────┐
    │              Auth                             │
    │  Keycloak :8180                               │
    └──────────────────────────────────────────────┘
```

### Service Directory

| Service | Port | Language | Role |
|---------|------|----------|------|
| Gateway | 8080 | Kotlin | API gateway, JWT auth, routing, WebSocket proxy |
| Position Service | 8081 | Kotlin | Trade booking, position calculation, event sourcing |
| Price Service | 8082 | Kotlin | Price ingestion, Redis caching, Kafka publishing |
| Risk Orchestrator | 8083 | Kotlin | Coordinates risk calculations, gRPC client to Risk Engine |
| Audit Service | 8084 | Kotlin | Immutable audit log from Kafka trade events |
| Regulatory Service | 8085 | Kotlin | FRTB regulatory reporting |
| Notification Service | 8086 | Kotlin | Risk alerts and anomaly notifications |
| Rates Service | 8088 | Kotlin | Yield curves, risk-free rates, forward curves |
| Reference Data Service | 8089 | Kotlin | Dividend yields, credit spreads |
| Volatility Service | 8090 | Kotlin | Volatility surfaces for options pricing |
| Correlation Service | 8091 | Kotlin | Correlation matrices for portfolio risk |
| Risk Engine | 50051 (gRPC), 9091 (metrics) | Python | VaR, Monte Carlo, Greeks, ML models |
| UI | 5173 (dev) | TypeScript | React trading dashboard |

---

## Languages and Runtimes

| Language | Version | Where Used |
|----------|---------|------------|
| Kotlin | 2.1.20 | All backend services (JVM 21) |
| Python | >= 3.12 | Risk Engine |
| TypeScript | ~5.9.3 | React UI |
| Proto3 | — | gRPC service definitions |

---

## Build Tooling

| Tool | Version | Scope |
|------|---------|-------|
| Gradle (Kotlin DSL) | — | Kotlin services, convention plugins, version catalog |
| build-logic/convention | — | Shared plugins: `kinetix.kotlin-common`, `kinetix.kotlin-library`, `kinetix.kotlin-service`, `kinetix.kotlin-testing`, `kinetix.protobuf` |
| uv | — | Python dependency management and virtual environment |
| Hatchling | — | Python package build backend |
| Vite | ^7.3.1 | UI bundling and dev server |
| npm | — | UI dependency management |

### Gradle Project Graph

```
settings.gradle.kts
├── proto                  (protobuf definitions)
├── common                 (shared Kotlin library)
├── gateway
├── position-service
├── price-service
├── rates-service
├── risk-orchestrator
├── regulatory-service
├── notification-service
├── audit-service
├── reference-data-service
├── volatility-service
├── correlation-service
└── end2end-tests
```

---

## Web Frameworks

### Ktor 3.1.3 (Kotlin Services)

All backend services run on Ktor with Netty. The gateway installs the full plugin set; other services use a subset.

| Plugin | Module | Purpose |
|--------|--------|---------|
| ContentNegotiation | ktor-server-content-negotiation | JSON request/response via kotlinx.serialization |
| StatusPages | ktor-server-status-pages | Structured error responses |
| CallLogging | ktor-server-call-logging | Request/response logging |
| CORS | ktor-server-cors | Cross-origin for UI |
| WebSockets | ktor-server-websockets | Real-time market data streaming |
| Auth / JWT | ktor-server-auth-jwt | Keycloak JWT validation |
| Micrometer | ktor-server-metrics-micrometer | Prometheus `/metrics` endpoint |

### React 19 (UI)

| Library | Version | Purpose |
|---------|---------|---------|
| React | ^19.2.0 | Component framework |
| React DOM | ^19.2.0 | DOM rendering |
| Tailwind CSS | ^4.2.0 | Utility-first styling |
| Vite Plugin React | ^5.1.1 | HMR and JSX transform |
| ESLint | ^9.39.1 | Linting |

---

## Database and Persistence

### PostgreSQL 17 / TimescaleDB

A single PostgreSQL instance (TimescaleDB image) hosts per-service databases.

| Database | Service | Key Tables |
|----------|---------|------------|
| kinetix_position | Position Service | trade_events (V1), positions (V2) |
| kinetix_price | Price Service | market_data (V1) — TimescaleDB hypertable |
| kinetix_audit | Audit Service | audit_events (V1) |
| kinetix_gateway | Gateway | — |
| kinetix_risk | Risk Orchestrator | — |
| kinetix_notification | Notification Service | — |
| kinetix_regulatory | Regulatory Service | — |
| kinetix_rates | Rates Service | yield_curves, yield_curve_points, risk_free_rates, forward_curves, forward_curve_points |
| kinetix_reference_data | Reference Data Service | dividend_yields, credit_spreads |
| kinetix_volatility | Volatility Service | volatility_surfaces, volatility_surface_data |
| kinetix_correlation | Correlation Service | correlation_matrices |

### ORM and Database Libraries

| Library | Version | Role |
|---------|---------|------|
| Exposed | 0.58.0 | Kotlin SQL framework (core, dao, jdbc, kotlin-datetime, json) |
| Flyway | 11.3.1 | Schema migrations (core + postgresql module) |
| HikariCP | 6.2.1 | JDBC connection pooling |
| PostgreSQL JDBC | 42.7.5 | JDBC driver |

---

## Messaging and Streaming

### Apache Kafka 3.9.0

Runs in KRaft mode (no ZooKeeper). All topics have 3 partitions and replication factor 1.

| Topic | Producer | Consumer(s) | Payload |
|-------|----------|-------------|---------|
| `trades.lifecycle` | Position Service | Audit Service, Risk Orchestrator | Trade events (booked, amended, cancelled) |
| `price.updates` | Price Service | Position Service, Risk Orchestrator | Price updates |
| `risk.results` | Risk Orchestrator | Notification Service | VaR and risk calculation results |
| `rates.yield-curves` | Rates Service | Risk Orchestrator | Yield curve snapshots |
| `rates.risk-free` | Rates Service | Risk Orchestrator | Risk-free rate updates |
| `rates.forwards` | Rates Service | Risk Orchestrator | Forward curve snapshots |
| `reference-data.dividends` | Reference Data Service | Risk Orchestrator | Dividend yield updates |
| `reference-data.credit-spreads` | Reference Data Service | Risk Orchestrator | Credit spread updates |
| `volatility.surfaces` | Volatility Service | Risk Orchestrator | Volatility surface snapshots |
| `correlation.matrices` | Correlation Service | Risk Orchestrator | Correlation matrix updates |

---

## Caching

### Redis 7

| Client | Version | Service | Use Case |
|--------|---------|---------|----------|
| Lettuce | 6.5.3.RELEASE | Price Service | Cache latest prices for fast lookup |
| Lettuce | 6.5.3.RELEASE | Rates Service | Cache latest yield curves and rates |
| Lettuce | 6.5.3.RELEASE | Reference Data Service | Cache latest dividends and credit spreads |
| Lettuce | 6.5.3.RELEASE | Volatility Service | Cache latest volatility surfaces |
| Lettuce | 6.5.3.RELEASE | Correlation Service | Cache latest correlation matrices |

---

## Inter-Service Communication

### gRPC 1.70.0 + Protobuf 4.29.3

Proto definitions live in `proto/src/main/proto/kinetix/`.

| Service | Proto File | Methods |
|---------|-----------|---------|
| RiskCalculationService | `risk/risk_calculation.proto` | `CalculateVaR`, `CalculateVaRStream` (bidirectional) |

**Common types** (`common/types.proto`): `Money`, `PortfolioId`, `TradeId`, `InstrumentId`, `Position`, `AssetClass` enum (EQUITY, FIXED_INCOME, FX, COMMODITY, DERIVATIVE).

### Communication Patterns

| Pattern | Where Used |
|---------|------------|
| gRPC unary | Risk Orchestrator → Risk Engine (single VaR request) |
| gRPC streaming | Risk Orchestrator → Risk Engine (batch VaR) |
| HTTP/REST | UI → Gateway → backend services |
| WebSocket | Gateway → UI (real-time market data) |
| Kafka async | Trade events, price updates, risk results |

---

## Serialization

| Format | Library | Version | Where Used |
|--------|---------|---------|------------|
| JSON | kotlinx.serialization | 1.8.0 | REST APIs, Kafka payloads |
| Protobuf binary | protobuf-kotlin | 4.29.3 | gRPC messages |

---

## Dependency Injection

| Library | Version | Modules |
|---------|---------|---------|
| Koin | 4.0.4 | koin-core, koin-ktor, koin-test |

---

## Authentication and Security

### Keycloak 24.0

Runs in dev mode with realm auto-import. Client: `kinetix-api` (public, direct access grants).

| Role | Permissions |
|------|------------|
| ADMIN | All permissions |
| TRADER | READ_PORTFOLIOS, WRITE_TRADES, READ_POSITIONS, READ_RISK, CALCULATE_RISK |
| RISK_MANAGER | READ_PORTFOLIOS, READ_POSITIONS, READ_RISK, CALCULATE_RISK, READ_ALERTS |
| COMPLIANCE | READ_PORTFOLIOS, READ_POSITIONS, READ_RISK, READ_REGULATORY, GENERATE_REPORTS, READ_ALERTS |
| VIEWER | READ_PORTFOLIOS, READ_POSITIONS, READ_RISK, READ_ALERTS |

**Default dev users**: admin/admin, trader1/trader1, risk_mgr/risk_mgr, compliance1/compliance1, viewer1/viewer1.

### Gateway Routes and Permissions

| Route | Permission |
|-------|-----------|
| `GET /api/v1/portfolios` | READ_PORTFOLIOS |
| `POST /api/v1/portfolios/{id}/trades` | WRITE_TRADES |
| `GET /api/v1/portfolios/{id}/positions` | READ_POSITIONS |
| `GET /api/v1/risk/var/*` | CALCULATE_RISK |
| Stress test routes | READ_RISK |
| Regulatory routes | READ_REGULATORY |
| Notification routes | READ_ALERTS |

---

## Machine Learning and Scientific Computing

### Python Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| PyTorch | >= 2.2 | Neural network models |
| scikit-learn | >= 1.4 | Classical ML models |
| NumPy | >= 1.26 | Numerical arrays |
| SciPy | >= 1.12 | Statistical functions |
| joblib | >= 1.3 | Model serialization |

### ML Models (`risk-engine/src/kinetix_risk/ml/`)

| Module | Purpose |
|--------|---------|
| `anomaly_detector.py` | Anomaly detection on risk metrics |
| `credit_model.py` | Credit risk scoring |
| `vol_predictor.py` | Volatility surface prediction |
| `model_store.py` | Model persistence and versioning |
| `data_generator.py` | Synthetic training data generation |

### Risk Calculations

| Module | Calculation |
|--------|-------------|
| `var_historical.py` | Historical VaR |
| `var_parametric.py` | Parametric VaR |
| `var_monte_carlo.py` | Monte Carlo VaR |
| `expected_shortfall.py` | Expected Shortfall (CVaR) |
| `greeks.py` | Options Greeks (delta, gamma, vega, theta, rho) |
| `portfolio_risk.py` | Portfolio-level risk aggregation |
| `volatility.py` | Volatility calculations |
| `frtb/calculator.py` | FRTB capital charge |
| `frtb/sbm.py` | Sensitivity-Based Method |
| `frtb/drc.py` | Default Risk Charge |
| `frtb/rrao.py` | Residual Risk Add-On |
| `stress/engine.py` | Stress testing engine |
| `stress/scenarios.py` | Predefined stress scenarios |

---

## Observability

### Collection Pipeline

```
Services (Ktor/Python)
  ├─ /metrics ──────► Prometheus :9090 ──► Grafana :3000
  └─ OTLP ──► OTel Collector :4317
                 ├─ traces ──► Tempo :3200 ──► Grafana
                 ├─ logs   ──► Loki :3100  ──► Grafana
                 └─ metrics ─► Prometheus (remote write)
```

### Libraries

| Library | Version | Role |
|---------|---------|------|
| Micrometer | 1.14.4 | Prometheus metrics registry for Ktor |
| OpenTelemetry | 1.46.0 | Traces and logs (API, SDK, OTLP exporter) |
| prometheus_client | >= 0.21 | Python metrics for Risk Engine |

### Grafana Dashboards

| Dashboard | Focus |
|-----------|-------|
| system-health.json | Service status, JVM metrics, response times |
| risk-overview.json | VaR values, calculation latency, risk breaches |
| market-data.json | Price staleness, ingestion rates |

### Prometheus Alert Rules

| Alert | Severity | Condition |
|-------|----------|-----------|
| VaRBreached | critical | VaR > 1M for 5 min |
| RiskCalculationSlow | warning | p95 latency > 30s for 2 min |
| MarketDataStale | warning | Staleness > 60s for 5 min |
| KafkaConsumerLag | warning | Lag > 10k for 10 min |

---

## Testing Strategy

Four-tier test pyramid. All tiers run in CI.

### Kotlin (Backend Services)

| Layer | Framework | Details |
|-------|-----------|---------|
| Unit | Kotest 5.9.1 + MockK 1.13.16 | Spec styles, property-based testing |
| Integration | Testcontainers 1.20.5 | PostgreSQL, Kafka containers |
| Acceptance | Ktor test host | End-to-end API scenarios |
| Load | Gatling 3.13.4 | GatewaySimulation, StressTestSimulation |

### Python (Risk Engine)

| Layer | Framework | Details |
|-------|-----------|---------|
| Unit | pytest >= 8.0 | VaR, Greeks, ML model tests |

### UI (Frontend)

| Layer | Framework | Details |
|-------|-----------|---------|
| Unit | Vitest ^4.0.18 | Component and hook tests |
| Component | Testing Library React ^16.3.2 | DOM interaction tests |

### Load Tests (Gatling)

| Simulation | Profile | Assertions |
|------------|---------|------------|
| GatewaySimulation | Ramp 100 users over 60s, then 20 rps sustained | p99 < 3s, success > 99% |
| StressTestSimulation | Ramp to 500 users over 300s | p99 < 3s, success > 99% |

---

## CI/CD

### GitHub Actions Workflows

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `ci.yml` | Push/PR to main | changes, kotlin-build, kotlin-integration, acceptance, python-build, ui-build |
| `load-test.yml` | Manual (workflow_dispatch) | Gatling simulation (GatewaySimulation or StressTestSimulation) |
| `dependency-review.yml` | PR to main | Fails on high-severity vulnerabilities |

**CI Runtime**: Java 21 (Temurin), Python 3.12 (uv), Node.js 22.

---

## Infrastructure (Docker Compose)

Three compose files in `infra/` for local development.

| File | Services | Key Ports |
|------|----------|-----------|
| `docker-compose.infra.yml` | PostgreSQL/TimescaleDB, Kafka (KRaft), Redis | 5432, 9092, 6379 |
| `docker-compose.observability.yml` | Prometheus, Grafana, Loki, Tempo, OTel Collector | 9090, 3000, 3100, 3200, 4317/4318 |
| `docker-compose.auth.yml` | Keycloak | 8180 |

### Startup Commands

```bash
# Infrastructure (database, messaging, cache)
docker compose -f infra/docker-compose.infra.yml up -d

# Observability (metrics, logs, traces)
docker compose -f infra/docker-compose.observability.yml up -d

# Authentication
docker compose -f infra/docker-compose.auth.yml up -d
```

---

## Repository Structure

```
kinetix/
├── build-logic/convention/       # Gradle convention plugins
├── proto/                        # Protobuf/gRPC definitions
├── common/                       # Shared Kotlin library
├── gateway/                      # API gateway (Ktor)
├── position-service/             # Trade booking and positions
├── price-service/                # Price ingestion and caching
├── rates-service/                # Yield curves, risk-free rates, forwards
├── reference-data-service/       # Dividend yields, credit spreads
├── volatility-service/           # Volatility surfaces
├── correlation-service/          # Correlation matrices
├── risk-orchestrator/            # Risk calculation coordinator
├── audit-service/                # Immutable audit log
├── regulatory-service/           # FRTB regulatory reporting
├── notification-service/         # Risk alerts
├── risk-engine/                  # Python VaR/Greeks/ML (gRPC)
│   └── src/kinetix_risk/
│       ├── var_*.py              # VaR calculations
│       ├── greeks.py             # Options Greeks
│       ├── frtb/                 # FRTB capital calculations
│       ├── ml/                   # ML models (anomaly, credit, vol)
│       └── stress/               # Stress testing engine
├── ui/                           # React + TypeScript frontend
├── end2end-tests/                # End-to-end API tests
├── load-tests/                   # Gatling performance tests
├── infra/                        # Docker Compose and config
│   ├── docker-compose.infra.yml
│   ├── docker-compose.observability.yml
│   ├── docker-compose.auth.yml
│   ├── db/init/                  # Database init scripts
│   ├── kafka/                    # Topic creation scripts
│   ├── keycloak/                 # Realm export
│   ├── prometheus/               # Scrape config and alert rules
│   ├── grafana/provisioning/     # Datasources and dashboards
│   ├── otel-collector/           # OTel Collector config
│   ├── loki/                     # Loki config
│   └── tempo/                    # Tempo config
├── docs/                         # Documentation
│   ├── adr/                      # Architecture Decision Records
│   ├── plan.md                   # Project roadmap
│   ├── tech-stack.md             # This file
│   ├── risk-calculation.md       # Risk calculation architecture
│   └── market-data-services-plan.md  # Market data services design
├── gradle/libs.versions.toml     # Version catalog
└── settings.gradle.kts           # Gradle project includes
```

---

## Key Design Decisions

All architecture decisions are documented as ADRs in `docs/adr/`.

| ADR | Title | Status |
|-----|-------|--------|
| [0001](adr/0001-use-monorepo-structure.md) | Use Monorepo Structure | Accepted |
| [0002](adr/0002-ktor-over-spring-boot.md) | Use Ktor Over Spring Boot for Kotlin Services | Accepted |
| [0003](adr/0003-grpc-for-python-integration.md) | Use gRPC for Kotlin-Python Integration | Accepted |
| [0004](adr/0004-kafka-for-async-messaging.md) | Use Apache Kafka for Asynchronous Messaging | Accepted |
| [0005](adr/0005-timescaledb-for-time-series.md) | Use TimescaleDB for Time-Series Data | Accepted |
| [0006](adr/0006-selective-event-sourcing.md) | Selective Event Sourcing for Trade Lifecycle | Accepted |
| [0007](adr/0007-kotest-for-testing.md) | Use Kotest Over JUnit 5 for Kotlin Testing | Accepted |
| [0008](adr/0008-grafana-stack-for-observability.md) | Use Grafana Stack for Observability | Accepted |
| [0009](adr/0009-exposed-for-database-access.md) | Use Exposed for Database Access | Accepted |
| [0010](adr/0010-react-vite-for-frontend.md) | Use React + Vite for Frontend | Accepted |

---

## Version Index

Alphabetical list of all dependencies and their pinned/minimum versions.

| Dependency | Version | Source |
|------------|---------|--------|
| ESLint | ^9.39.1 | ui/package.json |
| Exposed | 0.58.0 | libs.versions.toml |
| Flyway | 11.3.1 | libs.versions.toml |
| Gatling Gradle Plugin | 3.13.4 | load-tests/build.gradle.kts |
| Grafana | latest | docker-compose.observability.yml |
| gRPC | 1.70.0 | libs.versions.toml |
| gRPC Kotlin | 1.4.3 | libs.versions.toml |
| grpcio (Python) | >= 1.60 | pyproject.toml |
| Hatchling | latest | pyproject.toml |
| HikariCP | 6.2.1 | libs.versions.toml |
| joblib | >= 1.3 | pyproject.toml |
| Kafka | 3.9.0 | libs.versions.toml |
| Keycloak | 24.0 | docker-compose.auth.yml |
| Koin | 4.0.4 | libs.versions.toml |
| Kotest | 5.9.1 | libs.versions.toml |
| Kotlin | 2.1.20 | libs.versions.toml |
| kotlinx-coroutines | 1.10.1 | libs.versions.toml |
| kotlinx-datetime | 0.6.2 | libs.versions.toml |
| kotlinx-serialization | 1.8.0 | libs.versions.toml |
| Ktor | 3.1.3 | libs.versions.toml |
| Lettuce (Redis) | 6.5.3.RELEASE | libs.versions.toml |
| Logback | 1.5.16 | libs.versions.toml |
| Loki | latest | docker-compose.observability.yml |
| Micrometer | 1.14.4 | libs.versions.toml |
| MockK | 1.13.16 | libs.versions.toml |
| NumPy | >= 1.26 | pyproject.toml |
| OpenTelemetry | 1.46.0 | libs.versions.toml |
| PostgreSQL (TimescaleDB) | 17 | docker-compose.infra.yml |
| PostgreSQL JDBC | 42.7.5 | libs.versions.toml |
| Prometheus | latest | docker-compose.observability.yml |
| prometheus_client (Python) | >= 0.21 | pyproject.toml |
| Protobuf | 4.29.3 | libs.versions.toml |
| Protobuf Gradle Plugin | 0.9.4 | libs.versions.toml |
| protobuf (Python) | >= 4.25 | pyproject.toml |
| pytest | >= 8.0 | pyproject.toml |
| PyTorch | >= 2.2 | pyproject.toml |
| React | ^19.2.0 | ui/package.json |
| React DOM | ^19.2.0 | ui/package.json |
| Redis | 7 | docker-compose.infra.yml |
| scikit-learn | >= 1.4 | pyproject.toml |
| SciPy | >= 1.12 | pyproject.toml |
| SLF4J | 2.0.17 | libs.versions.toml |
| Tailwind CSS | ^4.2.0 | ui/package.json |
| Tempo | latest | docker-compose.observability.yml |
| Testcontainers | 1.20.5 | libs.versions.toml |
| Testing Library React | ^16.3.2 | ui/package.json |
| TypeScript | ~5.9.3 | ui/package.json |
| Vite | ^7.3.1 | ui/package.json |
| Vitest | ^4.0.18 | ui/package.json |
