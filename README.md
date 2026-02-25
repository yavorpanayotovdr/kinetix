# Kinetix

Real-time portfolio risk management platform built as a polyglot microservices monorepo.

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
| Frontend | React 19, Tailwind CSS 4, Vite 7 |
| Database | PostgreSQL 17 / TimescaleDB |
| Messaging | Apache Kafka 3.9 (KRaft) |
| Caching | Redis 7 |
| Inter-service | gRPC 1.70 / Protobuf 4.29 |
| Auth | Keycloak 24.0 (OAuth2/OIDC) |
| ML / Numerics | PyTorch, scikit-learn, NumPy, SciPy |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana, Loki, Tempo |
| Build | Gradle 9.3 (Kotlin DSL, convention plugins), uv, npm |
| Testing | Kotest, Testcontainers, MockK, pytest, Vitest, Gatling |
| CI/CD | GitHub Actions |

## Services

| Service | Port | Description |
|---|---|---|
| Gateway | 8080 | API routing, JWT authentication, WebSocket proxy |
| Position Service | 8081 | Trade booking, position calculation, event sourcing |
| Price Service | 8082 | Price ingestion, Redis caching, Kafka publishing |
| Risk Orchestrator | 8083 | Coordinates VaR and risk calculations via gRPC |
| Audit Service | 8084 | Immutable audit log from Kafka trade events |
| Regulatory Service | 8085 | FRTB regulatory reporting |
| Notification Service | 8086 | Risk breach alerts and anomaly notifications |
| Rates Service | 8088 | Yield curves, risk-free rates, forward curves |
| Reference Data Service | 8089 | Dividend yields, credit spreads |
| Volatility Service | 8090 | Volatility surfaces for options pricing |
| Correlation Service | 8091 | Correlation matrices for portfolio risk |
| Risk Engine | 50051 | VaR, Monte Carlo, Greeks, ML models (Python/gRPC) |
| UI | 5173 | React trading and risk dashboard |

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
./gradlew build -x integrationTest -x acceptanceTest
```

### Kotlin (integration tests)

```bash
./gradlew :position-service:integrationTest
./gradlew :price-service:integrationTest
./gradlew :risk-orchestrator:integrationTest
./gradlew :audit-service:integrationTest
```

### Kotlin (acceptance tests)

```bash
./gradlew :acceptance-tests:acceptanceTest
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
- [`docs/risk-calculation.md`](docs/risk-calculation.md) - Risk calculation architecture and data flow
- [`docs/plan.md`](docs/plan.md) - Development roadmap and increments
- [`docs/adr/`](docs/adr/) - Architecture Decision Records

## Project Structure

```
kinetix/
├── gateway/               Ktor API gateway
├── position-service/      Trade booking and positions
├── price-service/         Price ingestion pipeline
├── rates-service/         Yield curves and risk-free rates
├── reference-data-service/ Dividend yields and credit spreads
├── volatility-service/    Volatility surfaces
├── correlation-service/   Correlation matrices
├── risk-orchestrator/     Risk calculation coordinator
├── audit-service/         Immutable audit trail
├── regulatory-service/    FRTB regulatory reporting
├── notification-service/  Risk alerts
├── risk-engine/           Python VaR/Greeks/ML engine
├── ui/                    React + TypeScript frontend
├── proto/                 Protobuf/gRPC definitions
├── common/                Shared Kotlin library
├── build-logic/           Gradle convention plugins
├── acceptance-tests/      End-to-end API tests
├── load-tests/            Gatling performance tests
├── infra/                 Docker Compose and infra config
├── deploy/                Docker, Helm, and Terraform configs
└── docs/                  Tech docs and ADRs
```
