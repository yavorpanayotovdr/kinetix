# ADR-0008: Use Grafana Stack (Prometheus + Loki + Tempo) for Observability

## Status
Accepted

## Context
Observability is a core requirement. We need metrics, logs, and distributed traces across Kotlin and Python services, queryable from a single UI. Options: Grafana stack (Prometheus + Loki + Tempo), ELK stack (Elasticsearch + Logstash + Kibana), Datadog/cloud-native (not viable for local docker-compose).

## Decision
Use the Grafana observability stack:
- **Prometheus** for metrics storage and alerting rules
- **Grafana Loki** for log aggregation
- **Grafana Tempo** for distributed trace storage
- **Grafana** as the single-pane-of-glass UI
- **OpenTelemetry Collector** as the vendor-neutral telemetry pipeline

## Consequences

### Positive
- Dramatically lower resource footprint than ELK — Loki and Tempo are lightweight (Elasticsearch alone needs 2GB+ heap)
- Practical for local docker-compose development on a developer laptop
- Single UI (Grafana) for all three pillars — jump from metrics to traces to logs seamlessly
- OpenTelemetry Collector decouples instrumentation from backends — can swap backends later without changing application code
- Prometheus alerting rules are widely understood and well-documented

### Negative
- Loki's log querying (LogQL) is less powerful than Elasticsearch's full-text search
- Tempo has no built-in indexing — trace lookup requires trace IDs (mitigated by linking from metrics/logs)
- Self-managed: no SaaS convenience (acceptable for local dev; production would use hosted Grafana Cloud)

### Telemetry Flow
```
App (Kotlin/Python) --OTLP--> OTel Collector ---> Prometheus (metrics)
                                              ---> Loki (logs)
                                              ---> Tempo (traces)
                                                        |
                                                    Grafana
```

### Alternatives Considered
- **ELK (Elasticsearch + Logstash + Kibana)**: Powerful full-text log search, mature ecosystem. But Elasticsearch's memory requirements (minimum 2GB heap, 4GB+ recommended) make it impractical for local docker-compose alongside 8 application services, Kafka, PostgreSQL, and Redis. Total memory exceeds what a developer laptop can comfortably provide.
