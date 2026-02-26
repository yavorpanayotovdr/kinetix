# Kinetix API Endpoints

## Service Map

```
                                    ┌─────────────────────────────┐
                                    │           UI (5173)          │
                                    │     React / Vite / Tailwind  │
                                    └──────────┬──────────────────┘
                                               │ HTTP + WebSocket
                                               ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              GATEWAY (8080)                                      │
│                                                                                  │
│  Positions        Risk/VaR         Regulatory      Notifications    System       │
│  ──────────       ──────────       ──────────      ──────────       ──────────   │
│  GET  /portfolios POST /var/{p}    POST /frtb/{p}  GET  /rules      GET /health  │
│  POST /trades     GET  /var/{p}    POST /report/{p}POST /rules      GET /metrics │
│  GET  /positions  POST /stress/{p} ────────────────DEL  /rules/{id} GET /system  │
│  ────────────     GET  /scenarios  Prices          GET  /alerts       /health    │
│  WebSocket        POST /greeks/{p} ──────────      ──────────────               │
│  ──────────       POST /deps/{p}   GET /latest     Jobs                          │
│  WS /ws/prices    GET  /jobs/{p}   GET /history    ──────────                    │
│                   GET  /jobs/                       GET /jobs/{p}                 │
│                     detail/{id}                     GET /jobs/detail/{id}         │
└───┬──────────┬──────────┬──────────┬─────────┬─────────┬─────────┬───────────────┘
    │          │          │          │         │         │         │
    ▼          ▼          ▼          ▼         ▼         ▼         ▼
┌────────┐┌────────┐┌────────┐┌──────────┐┌───────┐┌────────┐┌──────────┐
│Position││ Price  ││  Risk  ││Notific.  ││ Rates ││Ref Data││Volatility│
│Service ││Service ││Orchest.││Service   ││Service││Service ││Service   │
│ (8081) ││ (8082) ││ (8083) ││ (8086)   ││ (8088)││ (8089) ││ (8090)   │
└────────┘└────────┘└───┬────┘└──────────┘└───────┘└────────┘└──────────┘
                        │                                        ┌──────────┐
                        │ gRPC                                   │Correlat. │
                        ▼                                        │Service   │
                 ┌─────────────┐  ┌─────────┐ ┌──────────┐      │ (8091)   │
                 │ Risk Engine │  │  Audit  │ │Regulatory│      └──────────┘
                 │  (50051)    │  │ Service │ │ Service  │
                 │  Python     │  │ (8084)  │ │ (8085)   │
                 └─────────────┘  └─────────┘ └──────────┘
```

---

## Endpoints by Service

### Gateway (port 8080) — API Router & Auth

The gateway proxies all client requests to backend services, enforces JWT authentication, and handles WebSocket connections.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/system/health` | System-wide health (all services, dev mode) |
| `WS` | `/ws/prices` | Real-time price stream (subscribe/unsubscribe) |

**Positions (→ Position Service)**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/portfolios` | List all portfolios |
| `POST` | `/api/v1/portfolios/{portfolioId}/trades` | Book a new trade |
| `GET` | `/api/v1/portfolios/{portfolioId}/positions` | Get positions for a portfolio |

**Prices (→ Price Service)**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/prices/{instrumentId}/latest` | Latest price for an instrument |
| `GET` | `/api/v1/prices/{instrumentId}/history` | Price history (`from`, `to` params) |

**Risk (→ Risk Orchestrator)**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/risk/var/{portfolioId}` | Trigger VaR calculation |
| `GET` | `/api/v1/risk/var/{portfolioId}` | Get latest cached VaR |
| `POST` | `/api/v1/risk/stress/{portfolioId}` | Run stress test |
| `GET` | `/api/v1/risk/stress/scenarios` | List stress test scenarios |
| `POST` | `/api/v1/risk/greeks/{portfolioId}` | Calculate portfolio Greeks |
| `POST` | `/api/v1/risk/dependencies/{portfolioId}` | Discover market data dependencies |
| `GET` | `/api/v1/risk/jobs/{portfolioId}` | List valuation jobs (`limit`, `offset`, `from`, `to`) |
| `GET` | `/api/v1/risk/jobs/detail/{jobId}` | Get job execution details |

**Regulatory (→ Risk Orchestrator)**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/regulatory/frtb/{portfolioId}` | Calculate FRTB capital charges |
| `POST` | `/api/v1/regulatory/report/{portfolioId}` | Generate regulatory report (CSV/XBRL) |

**Notifications (→ Notification Service)**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/notifications/rules` | List alert rules |
| `POST` | `/api/v1/notifications/rules` | Create alert rule |
| `DELETE` | `/api/v1/notifications/rules/{ruleId}` | Delete alert rule |
| `GET` | `/api/v1/notifications/alerts` | Recent alerts (`limit` param, default 50) |

---

### Position Service (port 8081) — Trade Booking & Positions

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/portfolios` | List all portfolio IDs |
| `GET` | `/api/v1/portfolios/{portfolioId}/positions` | Get positions for a portfolio |
| `POST` | `/api/v1/portfolios/{portfolioId}/trades` | Book a trade (creates/updates position) |

**Kafka:** Publishes to `trades.lifecycle` · Consumes from `price.updates`

---

### Price Service (port 8082) — Price Ingestion & Distribution

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/prices/{instrumentId}/latest` | Latest price for an instrument |
| `GET` | `/api/v1/prices/{instrumentId}/history` | Price history (`from`, `to` params) |
| `POST` | `/api/v1/prices/ingest` | Ingest a new price point |

**Kafka:** Publishes to `price.updates`

---

### Risk Orchestrator (port 8083) — VaR, Stress Tests, FRTB

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `POST` | `/api/v1/risk/var/{portfolioId}` | Calculate VaR (`calculationType`, `confidenceLevel`, `timeHorizonDays`, `numSimulations`) |
| `GET` | `/api/v1/risk/var/{portfolioId}` | Latest cached VaR result |
| `POST` | `/api/v1/risk/stress/{portfolioId}` | Run stress test with custom shocks |
| `GET` | `/api/v1/risk/stress/scenarios` | List available historical scenarios |
| `POST` | `/api/v1/risk/greeks/{portfolioId}` | Calculate Greeks (Delta, Gamma, Vega, Theta, Rho) |
| `POST` | `/api/v1/risk/dependencies/{portfolioId}` | Discover market data dependencies |
| `GET` | `/api/v1/risk/jobs/{portfolioId}` | List valuation jobs (pagination + date range) |
| `GET` | `/api/v1/risk/jobs/detail/{jobId}` | Job execution details |
| `POST` | `/api/v1/regulatory/frtb/{portfolioId}` | Calculate FRTB capital charges |
| `POST` | `/api/v1/regulatory/report/{portfolioId}` | Generate regulatory report |

**Kafka:** Consumes from `trades.lifecycle`, `price.updates`, `rates.*` · Publishes to `risk.results`
**gRPC:** Calls Risk Engine on port 50051

---

### Notification Service (port 8086) — Alerts & Rules

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/notifications/rules` | List all alert rules |
| `POST` | `/api/v1/notifications/rules` | Create a new alert rule |
| `DELETE` | `/api/v1/notifications/rules/{ruleId}` | Delete an alert rule |
| `GET` | `/api/v1/notifications/alerts` | Recent alerts (`limit` param, default 50) |

**Kafka:** Consumes from `risk.results`, `trades.lifecycle`

---

### Rates Service (port 8088) — Yield Curves, Risk-Free Rates, Forwards

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/rates/yield-curves/{curveId}/latest` | Latest yield curve |
| `GET` | `/api/v1/rates/yield-curves/{curveId}/history` | Yield curve history (`from`, `to`) |
| `POST` | `/api/v1/rates/yield-curves` | Ingest yield curve data |
| `GET` | `/api/v1/rates/risk-free/{currency}/latest` | Latest risk-free rate (`tenor` param) |
| `POST` | `/api/v1/rates/risk-free` | Ingest risk-free rate |
| `GET` | `/api/v1/rates/forwards/{instrumentId}/latest` | Latest forward curve |
| `GET` | `/api/v1/rates/forwards/{instrumentId}/history` | Forward curve history (`from`, `to`) |
| `POST` | `/api/v1/rates/forwards` | Ingest forward curve data |

**Kafka:** Publishes to `rates.yield-curves`, `rates.risk-free`, `rates.forwards`

---

### Reference Data Service (port 8089) — Dividends & Credit Spreads

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/reference-data/dividends/{instrumentId}/latest` | Latest dividend yield |
| `POST` | `/api/v1/reference-data/dividends` | Ingest dividend yield data |
| `GET` | `/api/v1/reference-data/credit-spreads/{instrumentId}/latest` | Latest credit spread |
| `POST` | `/api/v1/reference-data/credit-spreads` | Ingest credit spread data |

---

### Volatility Service (port 8090) — Volatility Surfaces

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/volatility/{instrumentId}/surface/latest` | Latest volatility surface |
| `GET` | `/api/v1/volatility/{instrumentId}/surface/history` | Vol surface history (`from`, `to`) |
| `POST` | `/api/v1/volatility/surfaces` | Ingest volatility surface data |

---

### Correlation Service (port 8091) — Correlation Matrices

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/correlations/latest` | Latest correlation matrix (`labels`, `window` params) |
| `POST` | `/api/v1/correlations/ingest` | Ingest correlation matrix data |

---

### Audit Service (port 8084) — Immutable Audit Trail

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `GET` | `/api/v1/audit/events` | Get audit events (optional `portfolioId` filter) |

**Kafka:** Consumes from `trades.lifecycle`

---

### Regulatory Service (port 8085) — FRTB Reporting

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Service health check |
| `GET` | `/metrics` | Prometheus metrics |
| `POST` | `/api/v1/regulatory/frtb/{portfolioId}/calculate` | Calculate FRTB capital charges |
| `GET` | `/api/v1/regulatory/frtb/{portfolioId}/history` | FRTB calculation history (`limit`, `offset`) |
| `GET` | `/api/v1/regulatory/frtb/{portfolioId}/latest` | Latest FRTB calculation |

**Kafka:** Consumes from `risk.results`

---

### Risk Engine (gRPC port 50051, metrics 9091) — Python Compute

All endpoints are gRPC RPCs. No REST API.

**RiskCalculationService**

| RPC | Request → Response | Description |
|-----|-------------------|-------------|
| `Valuate` | `ValuationRequest → ValuationResponse` | Unified VaR + Greeks calculation (requested outputs: VAR, EXPECTED_SHORTFALL, GREEKS) |
| `CalculateVaR` | `VaRRequest → VaRResponse` | ~~Deprecated~~ — use `Valuate` instead |
| `CalculateVaRStream` | `stream VaRRequest → stream VaRResponse` | Bidirectional streaming VaR |

**StressTestService**

| RPC | Request → Response | Description |
|-----|-------------------|-------------|
| `RunStressTest` | `StressTestRequest → StressTestResponse` | Run stress test with market shocks |
| `ListScenarios` | `ListScenariosRequest → ListScenariosResponse` | List historical stress scenarios |
| `CalculateGreeks` | `GreeksRequest → GreeksResponse` | ~~Deprecated~~ — use `Valuate` with GREEKS output instead |

**RegulatoryReportingService**

| RPC | Request → Response | Description |
|-----|-------------------|-------------|
| `CalculateFrtb` | `FrtbRequest → FrtbResponse` | FRTB capital charges and components |
| `GenerateReport` | `GenerateReportRequest → GenerateReportResponse` | Regulatory report (CSV/XBRL) |

**MarketDataDependenciesService**

| RPC | Request → Response | Description |
|-----|-------------------|-------------|
| `DiscoverDependencies` | `DataDependenciesRequest → DataDependenciesResponse` | Discover required market data |

**MLPredictionService**

| RPC | Request → Response | Description |
|-----|-------------------|-------------|
| `PredictVolatility` | `VolatilityRequest → VolatilityResponse` | Single instrument vol prediction (LSTM) |
| `PredictVolatilityBatch` | `BatchVolatilityRequest → BatchVolatilityResponse` | Batch vol prediction |
| `ScoreCredit` | `CreditScoreRequest → CreditScoreResponse` | Credit default probability (GBT) |
| `DetectAnomaly` | `AnomalyRequest → AnomalyResponse` | Anomaly detection (Isolation Forest) |

| Protocol | Endpoint | Description |
|----------|----------|-------------|
| `HTTP GET` | `http://localhost:9091/metrics` | Prometheus metrics |

---

## Summary

```
Protocol Breakdown
──────────────────────────────────────────────
  REST (HTTP)     69 endpoints across 11 services
  gRPC            13 RPCs in 5 service definitions
  WebSocket        1 real-time price stream
──────────────────────────────────────────────
  Total           83

Method Distribution (REST only)
──────────────────────────────────────────────
  GET             40  ████████████████████  (58%)
  POST            26  █████████████         (38%)
  DELETE           2  █                     ( 3%)
  WebSocket        1  █                     ( 1%)
──────────────────────────────────────────────

Endpoints per Service (REST)
──────────────────────────────────────────────
  Gateway         23  ████████████████████████
  Risk Orchestr.  12  ████████████
  Rates Service   10  ██████████
  Notification     6  ██████
  Ref Data Svc     6  ██████
  Position Svc     5  █████
  Price Service    5  █████
  Volatility Svc   5  █████
  Regulatory Svc   5  █████
  Correlation Svc  4  ████
  Audit Service    3  ███
──────────────────────────────────────────────

Common Endpoints (all Kotlin services)
──────────────────────────────────────────────
  GET /health     Health check
  GET /metrics    Prometheus scrape target
──────────────────────────────────────────────
```
