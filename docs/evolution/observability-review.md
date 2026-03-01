# Observability Review: Logging, Dashboards & Infrastructure

*Date: 2026-03-01*
*Reviewed by: tech-support, architect, sre agents*

---

## Executive Summary

The Kinetix platform has a structurally sound observability stack (Prometheus + Loki + Tempo + OTel Collector + Grafana) with 12 Prometheus scrape targets, 12 Grafana dashboards, and an OTel-based log shipping pipeline. However, significant operational gaps prevent effective incident investigation:

- **Logging is sparse**: Most services have fewer than 5 log statements. Route handlers are completely silent. The risk-engine (Python) uses `print()`. The UI has zero logging.
- **5 dashboard panels query non-existent metrics** and permanently show "No data".
- **Distributed tracing is deployed but disabled** (`OTEL_TRACES_EXPORTER=none` on all services).
- **No Alertmanager** -- alert rules fire but notifications go nowhere.
- **No correlationId in MDC** -- cannot trace a business flow across services in Loki.
- **No HTTP request logging** -- `ktor-server-call-logging` is a dependency but never installed.

This document proposes a prioritized plan to close these gaps.

---

## Part 1: Application Logging Audit

### 1.1 Current State

All 11 Kotlin services use **SLF4J + Logback 1.5.16** with `AutoConfigOpenTelemetryAppender` shipping logs via OTel Collector to Loki. The Python risk-engine uses `print()` and a single `logger.warning()`. The React UI has no logging.

**Logback configuration** (identical across all Kotlin services):
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <appender name="OTEL" class="com.kinetix.common.observability.AutoConfigOpenTelemetryAppender">
        <captureExperimentalAttributes>true</captureExperimentalAttributes>
        <captureCodeAttributes>true</captureCodeAttributes>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="OTEL"/>
    </root>
</configuration>
```

### 1.2 Per-Service Log Coverage

| Service | Log Statements | Coverage | OTEL Shipping |
|---------|---------------|----------|---------------|
| risk-orchestrator | 22+ | Best -- VaR, P&L, SOD, Kafka consumers | Yes |
| notification-service | 8 | Moderate -- alert delivery, Kafka events | Yes |
| position-service | 4 | Minimal -- price updates, Kafka errors | Yes |
| audit-service | 1 | Minimal -- Kafka error only | Yes |
| price-service | 1 | Minimal -- Kafka publish error | Yes |
| rates-service | 1 | Minimal -- Kafka publish error | Yes |
| volatility-service | 1 | Minimal -- Kafka publish error | Yes |
| correlation-service | 1 | Minimal -- Kafka publish error | Yes |
| reference-data-service | 1 | Minimal -- Kafka publish error | Yes |
| gateway | 0 | None (1 StatusPages error handler) | Yes |
| regulatory-service | 0 | None (only DevDataSeeder) | Yes |
| risk-engine (Python) | 3 print + 1 warning | None (no real logging) | **No** |
| ui (React) | 0 | None | N/A |

### 1.3 Critical Logging Gaps

| # | Gap | Impact | Severity |
|---|-----|--------|----------|
| L1 | **No HTTP request/response logging** -- `ktor-server-call-logging` is a dependency but never installed in any Application.kt | No record of API calls, parameters, or response codes | Critical |
| L2 | **Zero logging in route handlers** -- all `*Routes.kt` files are silent | Trade booking, position queries, limit checks, regulatory operations produce no log output | Critical |
| L3 | **risk-engine uses `print()`, no OTEL** -- logs never reach Loki, only accessible via `kubectl logs` | The computational core is invisible in centralized logging | Critical |
| L4 | **No correlationId in MDC** -- correlationId exists on Kafka events but is never placed in SLF4J MDC | Cannot trace a business flow across services in Loki | Critical |
| L5 | **No userId/auth context in logs** -- no log line identifies who initiated an operation | Compliance gap for regulated trading platform | Critical |
| L6 | **gateway and regulatory-service have near-zero logging** | API gateway and compliance service are operationally invisible | High |
| L7 | **UI has no logging or error reporting** -- no console.log, no error boundary | JavaScript errors in production are invisible | High |
| L8 | **Plain text log format** -- no JSON structured encoder, no per-package level tuning | Fragile for log shipping, noisy Kafka/Netty logs at INFO | Medium |
| L9 | **No startup/shutdown lifecycle logging** | Services don't log configuration loaded or graceful shutdown | Medium |
| L10 | **Missing business events**: trade booking, limit breaches, regulatory state changes, stress test execution, model governance, cache hits/misses, WebSocket events | Gaps in business audit trail | High |

---

## Part 2: Grafana Dashboards Audit

### 2.1 Inventory

12 provisioned dashboards, 3 datasources (Prometheus/Loki/Tempo), 4 alert rules with tests.

| # | Dashboard | Panels | Status |
|---|-----------|--------|--------|
| 1 | Risk Overview | VaR gauge, trend, ES, component breakdown, calc count | 2 panels broken |
| 2 | System Health | Request rate, error rate, P95, JVM memory, GC, Kafka lag | 1 panel broken |
| 3 | Service Logs | Log volume, error count, warn count, log lines | Working |
| 4 | Price | Feed update rate, latency, staleness, by instrument | 3 of 4 panels broken |
| 5 | Gateway | Request rate, error rate, P95 latency, active requests | Working |
| 6 | Risk Orchestrator | VaR calc rate/duration, request rate, error rate | Working |
| 7-12 | 6 per-service dashboards | Request rate, error rate, P95, JVM memory (identical) | Working but redundant |

### 2.2 Broken Dashboard Panels (5 total)

| Panel | Dashboard | Queried Metric | Problem |
|-------|-----------|----------------|---------|
| Expected Shortfall | Risk Overview | `risk_var_expected_shortfall` | Metric never registered -- not in risk-engine/metrics.py |
| VaR Component Breakdown | Risk Overview | `risk_var_component_contribution` | Metric never registered |
| Feed Update Rate | Price | `price_updates_total` | Metric does not exist in price-service |
| Feed Latency | Price | `price_feed_latency_seconds_bucket` | Metric does not exist in price-service |
| Kafka Consumer Lag | System Health | `kafka_consumer_group_lag` | Requires Kafka exporter -- not deployed |

### 2.3 Configuration Issues

| Issue | Location | Fix |
|-------|----------|-----|
| Helm Tempo datasource URL wrong | `deploy/helm/kinetix/charts/observability/values.yaml:103` | Change port 3100 to 3200 |
| No explicit datasource UID on metric panels | All dashboard JSON files | Add `"datasource": {"type":"prometheus","uid":"prometheus"}` |
| 6 identical per-service dashboards | position, notification, rates, reference-data, volatility, correlation | Replace with 1 templated dashboard using `$service` variable |
| No dashboard links or drill-downs | All dashboards | Add inter-dashboard links and exemplar linking to Tempo |

### 2.4 Missing Dashboards

| # | Dashboard | Priority | Key Panels |
|---|-----------|----------|------------|
| D1 | **Risk Engine** | High | VaR calc rate/duration, Greeks, stress tests, ML predictions, FRTB, anomaly detection (13 existing metrics unvisualized) |
| D2 | **Kafka Health** | High | Consumer lag by group/topic, DLQ message counts, produce/consume throughput, partition distribution |
| D3 | **Database Health** | High | HikariCP pool active/idle/pending, connection acquire time, query duration, pool utilization % |
| D4 | **Trade Flow** | High | Trade submit/amend/cancel rates, pre-trade check pass/fail, position update latency, end-to-end trade-to-risk latency |
| D5 | **Service Overview (Templated)** | Medium | Replaces 6 identical dashboards -- request rate, error rate, P95, JVM memory, GC pause, thread count with `$service` variable |

### 2.5 Missing Alert Rules

| # | Alert | Expression | Severity |
|---|-------|-----------|----------|
| A1 | ServiceDown | `up == 0` for 1m | Critical |
| A2 | HighErrorRate | 5xx rate > 5% for 5m | Critical |
| A3 | HighLatency | p95 > 5s for 5m per service | Warning |
| A4 | JVMMemoryHigh | JVM heap usage > 90% for 5m | Warning |
| A5 | DLQMessagesPresent | DLQ message count > 0 | Warning |
| A6 | DBConnectionPoolExhausted | HikariCP pending > 0 for 2m | Critical |
| A7 | GrpcErrorRate | risk-engine gRPC error rate > 1% for 5m | Critical |

### 2.6 Proposed Dashboard Organization

```
Kinetix/
  Overview/
    - System Health
    - Service Overview (templated, replaces 6 copies)
  Risk/
    - Risk Overview
    - Risk Engine
    - Risk Orchestrator
  Trading/
    - Trade Flow
    - Position Service
    - Price
  Infrastructure/
    - Kafka Health
    - Database Health
    - Service Logs
  Auxiliary/
    - Notification Service
    - Gateway
```

---

## Part 3: Infrastructure Observability Audit

### 3.1 Deployed Stack

**Local Development** (Docker Compose):

| Component | Image | Port |
|-----------|-------|------|
| OTel Collector | `otel/opentelemetry-collector-contrib` | 4317, 4318 |
| Prometheus | `prom/prometheus` | 9090 |
| Grafana | `grafana/grafana` | 3000 |
| Loki | `grafana/loki` | 3100 |
| Tempo | `grafana/tempo` | 3200 |

**Kubernetes** (Helm): kube-prometheus-stack ~67.0, Loki ~6.0, Tempo ~1.0, OTel Collector ~0.100

### 3.2 Log Flow

```
Kotlin Services --> Logback --> OTEL Appender --> OTel SDK --> OTLP --> OTel Collector --> Loki
                            \-> STDOUT (plain text)

Python risk-engine --> print()/logging --> STDOUT only (never reaches Loki)

UI --> Nginx access/error logs --> STDOUT (no OTel)
```

### 3.3 Metrics Flow

```
Kotlin Services --> Micrometer + PrometheusMeterRegistry --> GET /metrics --> Prometheus scrapes
Python risk-engine --> prometheus_client --> :9091/metrics --> Prometheus scrapes
```

12 scrape jobs configured. All Kotlin services expose `/metrics` via Micrometer. Risk-engine uses `prometheus_client` on port 9091.

### 3.4 Trace Flow (Currently Disabled)

```
OTel Collector --> Tempo (deployed, receiving zero traces)
All services: OTEL_TRACES_EXPORTER=none
```

### 3.5 Infrastructure Gaps

| # | Gap | Impact | Severity |
|---|-----|--------|----------|
| I1 | **4 services missing Helm charts** (rates, reference-data, volatility, correlation) | No K8s deployment, no OTEL env vars | Critical |
| I2 | **Tracing deployed but disabled** -- OTEL_TRACES_EXPORTER=none on all services | Tempo receives zero traces, entire trace infrastructure unused | Critical |
| I3 | **No Alertmanager** -- alert rules fire but have no delivery target | Alerts go nowhere (no email/Slack/PagerDuty) | Critical |
| I4 | **No log retention policies** -- Loki and Tempo have no retention configured | Disk fills unbounded in production | Critical |
| I5 | **risk-engine has no OTel logging** -- Python logs go to stdout only | Risk calculations invisible in Loki | Critical |
| I6 | **Tempo datasource URL wrong in Helm** -- points to :3100 instead of :3200 | Traces won't load in K8s-deployed Grafana | High |
| I7 | **No resource limits on observability components** in K8s | Prometheus/Loki can consume unbounded memory | High |
| I8 | **No infrastructure monitoring** -- Kafka, Redis, PostgreSQL metrics not scraped | Infrastructure blindspot | High |
| I9 | **Console logs are plain text, not JSON** | Fragile for log shipping | Medium |
| I10 | **Loki runs as SingleBinary, replication_factor=1** | Single point of failure | Medium |
| I11 | **Grafana anonymous access enabled** with admin/admin credentials | Must be secured for production | Medium |
| I12 | **No Prometheus remote_write** to durable storage | Metrics lost on restart, no long-term retention | Medium |
| I13 | **OTel Collector has no health checks** in Docker Compose | Cannot detect collector failures | Low |
| I14 | **Alert rule tests not integrated into CI** | `promtool test rules` not run automatically | Low |

---

## Part 4: Prioritized Improvement Plan

### Phase 1: Fix What's Broken (Effort: S-M)

These are bugs in the current setup that cause incorrect behavior.

| # | Item | Effort | Files |
|---|------|--------|-------|
| 1.1 | Fix Helm Tempo datasource URL (3100 -> 3200) | S | `deploy/helm/kinetix/charts/observability/values.yaml` |
| 1.2 | Register missing Prometheus metrics: `risk_var_expected_shortfall`, `risk_var_component_contribution` | S | `risk-engine/src/kinetix_risk/metrics.py`, `server.py` |
| 1.3 | Register missing metrics: `price_updates_total`, `price_feed_latency_seconds` | S | `price-service/.../metrics/` |
| 1.4 | Add explicit datasource UIDs to all dashboard JSON panels | S | All 12 dashboard JSON files |
| 1.5 | Add Alertmanager to docker-compose.observability.yml with basic webhook/email config | M | `infra/docker-compose.observability.yml`, new `infra/alertmanager/` config |

### Phase 2: Enable Core Observability (Effort: M-L)

Enable the three pillars: logging, metrics, tracing.

| # | Item | Effort | Files |
|---|------|--------|-------|
| 2.1 | **Install Ktor CallLogging** in all Kotlin services with correlationId MDC propagation | M | All `Application.kt` files, `logback.xml` |
| 2.2 | **Add correlationId to MDC** in all Kafka consumers (extract from event, put in MDC) | M | All `*Consumer.kt` files |
| 2.3 | **Enable distributed tracing** -- change OTEL_TRACES_EXPORTER from "none" to "otlp" in all Helm values | S | All `values.yaml` files |
| 2.4 | **Add Python OTel logging** to risk-engine -- replace `print()` with structured `logging`, add OTel Python SDK | M | `risk-engine/` -- new logging config, `pyproject.toml`, Helm values |
| 2.5 | **Add structured JSON log encoder** to logback.xml (e.g., `logstash-logback-encoder`) | M | `common/logback.xml`, `build.gradle.kts` |
| 2.6 | **Add per-package log level tuning** -- suppress Kafka/Netty noise, enable DEBUG for com.kinetix | S | `logback.xml` |

### Phase 3: Add Business Logging (Effort: M-L)

Add log statements where business events occur.

| # | Item | Effort | Services |
|---|------|--------|----------|
| 3.1 | **Log trade lifecycle events**: booking, amend, cancel with portfolioId, instrumentId, quantity, correlationId | M | position-service routes + services |
| 3.2 | **Log pre-trade limit checks**: pass/fail with limit type, current utilization | S | position-service LimitCheckService |
| 3.3 | **Log regulatory operations**: submission state changes, model governance actions, approval/rejection | M | regulatory-service routes + services |
| 3.4 | **Log audit events**: event received, persisted, hash chain verification results | S | audit-service |
| 3.5 | **Log gateway requests**: all proxied requests with method, path, status, latency | M | gateway |
| 3.6 | **Add userId to MDC** from auth context in all request handlers | M | All services with authenticated routes |
| 3.7 | **Add UI error boundary** with client-side error reporting | M | ui -- ErrorBoundary component |
| 3.8 | **Log service startup/shutdown** with configuration summary | S | All services |

### Phase 4: New Dashboards & Alerts (Effort: M-L)

| # | Item | Effort | Files |
|---|------|--------|-------|
| 4.1 | **Create Risk Engine dashboard** (13 existing metrics, 0 visualization) | M | `infra/grafana/provisioning/dashboards/risk-engine.json` |
| 4.2 | **Create Trade Flow dashboard** | M | New dashboard JSON |
| 4.3 | **Create Kafka Health dashboard** (requires Kafka exporter) | M | New dashboard JSON + docker-compose + prometheus scrape config |
| 4.4 | **Create Database Health dashboard** (requires HikariCP metrics registration) | M | New dashboard JSON + all `DatabaseFactory.kt` |
| 4.5 | **Consolidate 6 identical dashboards** into 1 templated Service Overview | M | Replace 6 JSON files with 1 templated |
| 4.6 | **Add 7 new alert rules** (ServiceDown, HighErrorRate, HighLatency, JVMMemoryHigh, DLQ, DBPool, gRPC) | M | `infra/prometheus/alert-rules.yml` |
| 4.7 | **Add dashboard links and drill-downs** between all dashboards | S | All dashboard JSON files |
| 4.8 | **Configure Grafana exemplars** for metrics-to-traces linking | S | Grafana datasource config |
| 4.9 | **Reorganize dashboards** into folder structure (Overview/Risk/Trading/Infrastructure/Auxiliary) | S | Dashboard JSON `folderTitle` fields |

### Phase 5: Infrastructure Hardening (Effort: M-L)

| # | Item | Effort | Files |
|---|------|--------|-------|
| 5.1 | **Add Kafka exporter** to docker-compose and Prometheus scrape config | M | `docker-compose.observability.yml`, `prometheus.yml` |
| 5.2 | **Register HikariCP with MeterRegistry** in all DatabaseFactory.kt | S | All `DatabaseFactory.kt` files |
| 5.3 | **Configure Loki retention** (e.g., 30 days for dev, 90 days for prod) | S | Loki config |
| 5.4 | **Configure Tempo retention** (e.g., 7 days for dev, 30 days for prod) | S | Tempo config |
| 5.5 | **Add resource limits** to observability components in Helm | S | `observability/values.yaml` |
| 5.6 | **Create Helm charts** for rates, reference-data, volatility, correlation services | L | `deploy/helm/kinetix/charts/` |
| 5.7 | **Add OTel Collector health checks** to docker-compose | S | `docker-compose.observability.yml` |
| 5.8 | **Integrate `promtool test rules`** into CI | S | CI pipeline config |
| 5.9 | **Secure Grafana** -- disable anonymous access, rotate admin credentials | S | Docker Compose + Helm values |

---

## Summary

The observability infrastructure is architecturally sound but operationally incomplete. The most impactful improvements are:

1. **Fix broken dashboards** (5 panels querying non-existent metrics) -- immediate, low effort
2. **Install HTTP request logging** -- single change that makes every API call visible
3. **Enable distributed tracing** -- one config change per service, Tempo is already deployed
4. **Add correlationId to MDC** -- enables cross-service flow tracing in Loki
5. **Add Alertmanager** -- without it, the 4 existing alert rules (and any new ones) are useless
6. **Create Risk Engine dashboard** -- 13 metrics from the computational core are completely unvisualized

The plan is organized into 5 phases, from quick fixes to infrastructure hardening, totaling ~45 individual action items.
