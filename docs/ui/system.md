# System Tab — Service Health & Observability

The System tab provides operational visibility into the health of all Kinetix microservices and links to the full Grafana observability stack for metrics, logs, and traces.

---

## What it displays

### Service Health Grid (left, 2/3 width)

A 3-column grid of cards for 9 monitored services:

| Service | Icon | Port |
|---------|------|------|
| Gateway | Globe | 8080 |
| Position Service | Briefcase | 8081 |
| Price Service | Dollar Sign | 8082 |
| Risk Orchestrator | Shield | 8083 |
| Notification Service | Bell | 8086 |
| Rates Service | Percent | 8088 |
| Reference Data Service | Database | 8089 |
| Volatility Service | Activity | 8090 |
| Correlation Service | Git Merge | 8091 |

Each card shows:
- **Status dot** — green (UP) with pulse animation, or red (DOWN)
- **Service name and icon**
- **Status text** — "UP" or "DOWN"
- **Grafana link** — direct link to that service's dashboard

### Overall Status Banner

- Green **"All Systems Operational"** when every service is UP
- Yellow **"Degraded"** when any service is DOWN
- Manual **Refresh** button

### Observability Links (right, 1/3 width)

| Link | Description | URL |
|------|-------------|-----|
| System Health | Request rate, error rate, latency, JVM, Kafka lag | Grafana dashboard |
| Risk Overview | VaR gauge, ES, component breakdown | Grafana dashboard |
| Service Logs | Log volume, errors, warnings, full log lines | Grafana/Loki dashboard |
| Prometheus | Raw metrics & alert rules | `localhost:9090` |
| Grafana | All dashboards | `localhost:3000` |

---

## How health checks work

The Gateway exposes a single aggregating endpoint that fans out to all downstream services:

```
UI polls GET /api/v1/system/health every 30 seconds
  → Gateway
    → parallel HTTP GET to each service's /health endpoint (2s timeout)
    → aggregate results
    → respond with overall status + per-service status
```

Each backend service (Ktor) exposes:
- `GET /health` — returns `{"status": "UP"}`
- `GET /metrics` — Prometheus metrics via Micrometer
- `GET /swagger` — Swagger UI for API docs

---

## Observability stack

```
Application Services (Kotlin/Python)
  ├── Metrics  →  Micrometer  →  Prometheus (port 9090)
  ├── Logs     →  OTel Collector  →  Loki (port 3100)
  └── Traces   →  OTel Collector  →  Tempo (port 3200)
                                        ↓
                                    Grafana (port 3000)
                                        ↓
                                    React UI (System tab)
```

### Prometheus

- Scrapes all 12 services every 15 seconds (11 Kotlin/Python services + risk-engine on port 9091)
- Three alert rule groups:
  - **kinetix-risk** — VaR breach (> $1M), calculation latency (P95 > 30s)
  - **kinetix-price** — Price staleness (> 60s since last update)
  - **kinetix-kafka** — Consumer lag (> 10,000 messages)

### Grafana Dashboards

Pre-provisioned dashboards for every service plus cross-cutting views:

| Dashboard | UID | Panels |
|-----------|-----|--------|
| System Health | `kinetix-system-health` | Request rate, error rate, P95 latency, JVM memory, GC pauses, Kafka lag |
| Risk Overview | `kinetix-risk-overview` | VaR gauge, ES, component breakdown |
| Service Logs | `kinetix-service-logs` | Log volume, errors, warnings, log lines |
| Per-service dashboards | `kinetix-{service}` | Service-specific metrics |

### Distributed Tracing (Tempo)

- Traces collected via OpenTelemetry OTLP (gRPC 4317, HTTP 4318)
- Linked to metrics and logs for end-to-end correlation

### Log Aggregation (Loki)

- OpenTelemetry Collector routes structured logs to Loki
- Queryable in Grafana with label-based filtering

---

## Why a trader / investment bank needs this

1. **Operational awareness** — A single pane of glass showing whether every service is healthy. If the price feed is down, traders need to know immediately.
2. **Degradation detection** — The yellow "Degraded" banner instantly surfaces partial outages, preventing traders from making decisions on stale data.
3. **Root cause investigation** — Grafana links from each service card let support teams drill into metrics, logs, and traces for the specific service that's misbehaving.
4. **SLA monitoring** — Request rate, error rate, and P95 latency metrics feed into SLA reporting for the trading platform.
5. **Proactive alerting** — Prometheus alert rules fire before users notice problems (e.g. consumer lag building up means calculations will be delayed).
6. **Compliance** — Audit teams require evidence that critical trading infrastructure is continuously monitored.

---

## Key files

| Component | Location |
|-----------|----------|
| UI Component | `ui/src/components/SystemDashboard.tsx` |
| System Health Hook | `ui/src/hooks/useSystemHealth.ts` |
| API Client | `ui/src/api/system.ts` |
| Gateway Health Endpoint | `gateway/src/main/kotlin/com/kinetix/gateway/Application.kt` |
| Prometheus Config | `infra/prometheus/prometheus.yml` |
| Alert Rules | `infra/prometheus/alert-rules.yml` |
| OTel Collector Config | `infra/otel-collector/otel-collector-config.yaml` |
| Loki Config | `infra/loki/loki-config.yaml` |
| Tempo Config | `infra/tempo/tempo-config.yaml` |
| Grafana Datasources | `infra/grafana/provisioning/datasources/datasources.yaml` |
| Grafana Dashboards | `infra/grafana/provisioning/dashboards/*.json` |
| Docker Compose (observability) | `infra/docker-compose.observability.yml` |

---

## API Endpoints

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/v1/system/health` | GET | Aggregated health status of all services |
| `/health` (per service) | GET | Individual service liveness check |
| `/metrics` (per service) | GET | Prometheus metrics endpoint |
