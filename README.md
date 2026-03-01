# Kinetix

Real-time portfolio risk management platform for institutional trading desks, built as a polyglot microservices monorepo.

Covers the full risk lifecycle: trade booking with pre-trade limit checks, position management with multi-currency aggregation, live P&L attribution, VaR/ES/Greeks computation (parametric, historical, Monte Carlo), Black-Scholes options pricing, stress testing, FRTB regulatory capital, model governance, and counterparty exposure monitoring.

## Architecture

```
                          ┌──────────┐
                          │    UI    │ :5173
                          └────┬─────┘
                               │
                          ┌────┴─────┐
                          │ Gateway  │ :8080
                          └────┬─────┘
              ┌────────────────┼────────────────┐
              │                │                 │
     ┌────────┴───┐   ┌───────┴───┐   ┌────────┴────────┐
     │ Position    │   │ Price     │   │ Risk            │
     │ Service     │   │ Service   │   │ Orchestrator    │
     │ :8081       │   │ :8082     │   │ :8083           │
     └─────┬───────┘   └─────┬────┘   └──┬─────────┬────┘
           │                 │            │         │ gRPC
           │          ┌──────┴────┐       │    ┌────┴─────┐
           │          │  Redis    │       │    │  Risk    │
           │          │  :6379    │       │    │  Engine  │
           │          └───────────┘       │    │  :50051  │
           │                              │    └──────────┘
           │    ┌─────────────────────────┘
           │    │  ┌─────────────────────────────────────┐
           │    │  │       Market Data Services           │
           │    │  │                                      │
           │    │  │  ┌──────────┐  ┌───────────────────┐ │
           │    │  │  │ Rates    │  │ Reference Data    │ │
           │    │  │  │ :8088    │  │ :8089             │ │
           │    │  │  └──────────┘  └───────────────────┘ │
           │    │  │  ┌──────────┐  ┌───────────────────┐ │
           │    │  │  │Volatility│  │ Correlation       │ │
           │    │  │  │ :8090    │  │ :8091             │ │
           │    │  │  └──────────┘  └───────────────────┘ │
           │    │  └─────────────────────────────────────┘
           │    │
     ┌─────┴────┴────────────────────────────────┐
     │              Kafka :9092                   │
     └───┬──────────┬──────────────┬─────────────┘
         │          │              │
  ┌──────┴───┐ ┌───┴──────────┐ ┌┴────────────┐
  │ Audit    │ │ Regulatory   │ │ Notification │
  │ Service  │ │ Service      │ │ Service      │
  │ :8084    │ │ :8085        │ │ :8086        │
  └──────────┘ └──────────────┘ └──────────────┘
                       │
              ┌────────┴────────┐
              │ PostgreSQL /    │
              │ TimescaleDB     │
              │ :5432           │
              └─────────────────┘
```

## Tech Stack

| Layer | Technology |
|---|---|
| Languages | Kotlin 2.1.20 (JVM 21), Python 3.12, TypeScript 5.9 |
| Web framework | Ktor 3.1.3 |
| Frontend | React 19, Tailwind CSS 4, Vite 7, lucide-react |
| Database | PostgreSQL 17 / TimescaleDB |
| Messaging | Apache Kafka 3.9 (KRaft) |
| Caching | Redis 7 |
| Inter-service | gRPC 1.70 / Protobuf 4.29 |
| Auth | Keycloak 24.0 (OAuth2/OIDC) |
| ML / Numerics | PyTorch, scikit-learn, NumPy, SciPy |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana, Loki, Tempo |
| Build | Gradle 9.3.1 (Kotlin DSL, convention plugins), uv, npm |
| Testing | Kotest, Testcontainers, MockK, pytest, Vitest, Gatling |
| CI/CD | GitHub Actions |

## Services

| Service | Port | Description |
|---|---|---|
| Gateway | 8080 | API routing, JWT auth (Keycloak), rate limiting, WebSocket proxy |
| Position Service | 8081 | Trade booking/amend/cancel, positions, limit checks, P&L, counterparty exposure |
| Price Service | 8082 | Price ingestion, TimescaleDB storage, Redis caching, Kafka publishing |
| Risk Orchestrator | 8083 | VaR/ES/Greeks, stress tests, P&L attribution, margin estimation, what-if analysis |
| Audit Service | 8084 | Hash-chained immutable audit trail with 7-year retention |
| Regulatory Service | 8085 | FRTB capital, model governance, submission workflow, VaR backtesting |
| Notification Service | 8086 | Risk breach alerts, anomaly detection, multi-channel delivery |
| Rates Service | 8088 | Yield curves, risk-free rates, forward curves |
| Reference Data Service | 8089 | Dividend yields, credit spreads |
| Volatility Service | 8090 | Volatility surfaces for options pricing |
| Correlation Service | 8091 | Correlation matrices with Ledoit-Wolf shrinkage |
| Risk Engine | 50051 | VaR, Monte Carlo, Black-Scholes, cross-Greeks, FRTB, ML models (Python/gRPC) |
| UI | 5173 | React trading and risk dashboard with dark mode |

## Quick Start

### Prerequisites

- **Java 21** (Temurin)
- **Python 3.12+** with [uv](https://docs.astral.sh/uv/)
- **Node.js 22** with npm
- **Docker** and Docker Compose

### Start the Dev Stack

```bash
./dev-up.sh
```

This starts infrastructure (Postgres, Kafka, Redis), observability (Grafana, Prometheus, Loki, Tempo), auth (Keycloak), all application services, and the UI.

### Stop the Dev Stack

```bash
./dev-down.sh
```

### Service URLs

| URL | Service |
|---|---|
| http://localhost:5173 | UI |
| http://localhost:8080 | Gateway API |
| http://localhost:3000 | Grafana (admin/admin) |
| http://localhost:9090 | Prometheus |
| http://localhost:8180 | Keycloak (admin/admin) |

### Default Users

| Username | Password | Role |
|---|---|---|
| trader1 | trader1 | TRADER |
| risk_mgr | risk_mgr | RISK_MANAGER |
| compliance1 | compliance1 | COMPLIANCE |
| viewer1 | viewer1 | VIEWER |
| admin | admin | ADMIN |

## Testing

### Kotlin (unit tests)

```bash
./gradlew test
```

### Kotlin (acceptance tests)

```bash
./gradlew acceptanceTest
```

### Kotlin (integration tests)

```bash
./gradlew :position-service:integrationTest
./gradlew :price-service:integrationTest
./gradlew :risk-orchestrator:integrationTest
./gradlew :audit-service:integrationTest
./gradlew :rates-service:integrationTest
./gradlew :reference-data-service:integrationTest
./gradlew :volatility-service:integrationTest
./gradlew :correlation-service:integrationTest
./gradlew :regulatory-service:integrationTest
```

### Kotlin (end-to-end tests)

```bash
./gradlew :end2end-tests:end2EndTest
```

### Python (risk engine)

```bash
cd risk-engine
uv sync
uv run pytest
```

### UI

```bash
cd ui
npm ci
npm run test
```

### Load tests (Gatling)

```bash
./gradlew :load-tests:gatlingRun
```

## Documentation

See the [project wiki](../../wiki) for detailed documentation including architecture decisions, tech stack deep-dive, and the development roadmap.

Key docs in the repo:

- [`docs/tech-stack.md`](docs/tech-stack.md) - Comprehensive technical reference
- [`docs/persistence.md`](docs/persistence.md) - Persistence layer reference (databases, schemas, repositories, data flow)
- [`docs/risk-calculation.md`](docs/risk-calculation.md) - Risk calculation architecture and data flow
- [`docs/api-endpoints.md`](docs/api-endpoints.md) - Full API endpoint reference
- [`docs/plan.md`](docs/plan.md) - Development roadmap and increments
- [`docs/evolution-report.md`](docs/evolution-report.md) - Project evolution history
- [`docs/adr/`](docs/adr/) - Architecture Decision Records
- [`docs/ui/`](docs/ui/) - UI tab documentation (Risk, Positions, Scenarios, Regulatory, Alerts, System)

## Key Capabilities

| Domain | Features |
|---|---|
| Trading | Trade booking, amendment, cancellation; pre-trade limit checks (position, notional, concentration); limit hierarchy (FIRM/DESK/TRADER/COUNTERPARTY) |
| Positions | Event-sourced positions; multi-currency aggregation with FX rates; realized P&L tracking; counterparty exposure (net/gross) |
| Risk | VaR/ES (parametric, historical, Monte Carlo with antithetic variates); EWMA volatility; P&L attribution; what-if analysis; SOD baseline snapshots |
| Greeks | Delta, Gamma, Vega, Theta, Rho; cross-Greeks (Vanna, Volga, Charm); Black-Scholes options pricing |
| Stress Testing | Predefined scenarios (GFC 2008, COVID 2020, Taper Tantrum 2013, Euro Crisis 2011); scenario governance with approval workflow |
| Regulatory | FRTB capital (SBM, DRC with 21 credit ratings, RRAO); VaR backtesting (Kupiec POF, Christoffersen); model governance; four-eyes submission workflow |
| Margin | SPAN/SIMM simplified margin estimation by asset class |
| Audit | Hash-chained immutable trail; correlation ID propagation; TimescaleDB 7-year retention with compression |
| UI | 8-tab dashboard; dark mode; CSV export; data quality monitoring; workspace customisation; multi-portfolio aggregation; WebSocket auto-reconnect; accessibility (WAI-ARIA) |

## Project Structure

```
kinetix/
├── gateway/                Ktor API gateway
├── position-service/       Trade booking, positions, limits, counterparty exposure
├── price-service/          Price ingestion pipeline
├── rates-service/          Yield curves and risk-free rates
├── reference-data-service/ Dividend yields and credit spreads
├── volatility-service/     Volatility surfaces
├── correlation-service/    Correlation matrices
├── risk-orchestrator/      Risk calculation coordinator
├── audit-service/          Hash-chained immutable audit trail
├── regulatory-service/     FRTB, model governance, regulatory submissions
├── notification-service/   Risk breach alerts and anomaly detection
├── risk-engine/            Python VaR/Greeks/Black-Scholes/FRTB/ML engine
├── ui/                     React trading and risk dashboard
├── proto/                  Protobuf/gRPC definitions
├── common/                 Shared Kotlin library (RetryableConsumer, domain types)
├── build-logic/            Gradle convention plugins
├── end2end-tests/          End-to-end API tests
├── load-tests/             Gatling performance tests
├── infra/                  Docker Compose and infra config
├── deploy/                 Docker, Helm, and Terraform configs
├── scripts/                CI and dev utility scripts
└── docs/                   Tech docs and ADRs
```
