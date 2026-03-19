# Market Data Alerting Plan

## Overview

The Kinetix market data pipeline has no automated alerting. When prices stop arriving, the VaR cache serves stale results, risk calculations become unreliable, and the UI data-quality indicator is the only visible signal — which requires someone to be actively watching the dashboard. This document defines the Prometheus alerting rules needed to detect pipeline failures automatically and the escalation path for each.

The pipeline has three observable failure modes:

1. **Price feed staleness** — no new price events arriving from external sources.
2. **Kafka consumer lag** — consumers falling behind on market data topics.
3. **VaR cache age** — the Redis cache serving results from a stale calculation.

All three must be alerted on separately because they can occur independently. A dead price feed causes both staleness and eventually cache age. A slow consumer causes lag and cache age but not feed staleness. A Redis failure causes cache age without affecting the feed or lag.

---

## Alerting Rules

### 1. Stale Prices

**Condition:** No new rows inserted into the `prices` hypertable in the last 5 minutes.

The metric is `kinetix_price_events_total` (a counter incremented by the price-service Kafka consumer on each `PriceEvent` processed). If the rate over 5 minutes is zero and the market is open, the feed is stale.

```yaml
- alert: MarketDataPriceFeedStale
  expr: |
    rate(kinetix_price_events_total[5m]) == 0
    and on() (hour() >= 8 and hour() < 17)  # market hours UTC
  for: 5m
  labels:
    severity: warning
    team: risk-platform
  annotations:
    summary: "Price feed has received no updates for 5 minutes"
    description: >
      No PriceEvents have been processed in the last 5 minutes during market hours.
      Check the price-service Kafka consumer lag and the upstream market data source.

- alert: MarketDataPriceFeedStaleCritical
  expr: |
    rate(kinetix_price_events_total[10m]) == 0
    and on() (hour() >= 8 and hour() < 17)
  for: 10m
  labels:
    severity: critical
    team: risk-platform
  annotations:
    summary: "Price feed stale for 10 minutes — VaR calculations are unreliable"
    description: >
      No PriceEvents processed for 10 minutes during market hours.
      All risk calculations are now using stale market data.
      Escalate immediately.
```

### 2. Kafka Consumer Lag

**Condition:** Any consumer group on a market data topic has lag exceeding 10,000 messages.

The metric is `kafka_consumer_group_lag` exposed by the Kafka JMX exporter. This covers all topics, filtered to the market data topics: `prices`, `rates`, `volatility`, `correlation`.

```yaml
- alert: MarketDataConsumerLagHigh
  expr: |
    kafka_consumer_group_lag{topic=~"kinetix\\.prices|kinetix\\.rates|kinetix\\.volatility|kinetix\\.correlation"}
    > 10000
  for: 5m
  labels:
    severity: warning
    team: risk-platform
  annotations:
    summary: "Kafka consumer lag on {{ $labels.topic }} is {{ $value }} messages"
    description: >
      Consumer group {{ $labels.group }} on topic {{ $labels.topic }} has
      {{ $value }} unprocessed messages. This will delay market data ingestion
      and may cause VaR cache staleness.

- alert: MarketDataConsumerLagCritical
  expr: |
    kafka_consumer_group_lag{topic=~"kinetix\\.prices|kinetix\\.rates|kinetix\\.volatility|kinetix\\.correlation"}
    > 20000
  for: 5m
  labels:
    severity: critical
    team: risk-platform
  annotations:
    summary: "Kafka consumer lag on {{ $labels.topic }} is critically high ({{ $value }} messages)"
    description: >
      Consumer group {{ $labels.group }} on topic {{ $labels.topic }} has
      {{ $value }} unprocessed messages. At current throughput this will take
      {{ $value | humanizeDuration }} to clear. Downstream risk calculations are degraded.
```

### 3. VaR Cache Age

**Condition:** The most recently written VaR result in Redis is older than 10 minutes.

The metric is `kinetix_var_cache_last_write_timestamp_seconds` (a gauge set by `RedisVaRCache.put()` to the Unix timestamp of the last successful write). When no write occurs for 10 minutes, the cache is serving stale results.

```yaml
- alert: VaRCacheStale
  expr: |
    (time() - kinetix_var_cache_last_write_timestamp_seconds) > 600
    and on() (hour() >= 8 and hour() < 17)
  for: 5m
  labels:
    severity: warning
    team: risk-platform
  annotations:
    summary: "VaR cache last updated {{ $value | humanizeDuration }} ago"
    description: >
      The VaR cache in Redis has not been updated in over 10 minutes.
      Risk calculation results displayed in the UI may be stale.
      Check risk-orchestrator consumer health and the price feed.

- alert: VaRCacheStaleCritical
  expr: |
    (time() - kinetix_var_cache_last_write_timestamp_seconds) > 1200
    and on() (hour() >= 8 and hour() < 17)
  for: 5m
  labels:
    severity: critical
    team: risk-platform
  annotations:
    summary: "VaR cache stale for over 20 minutes — risk dashboard is unreliable"
    description: >
      VaR cache last updated {{ $value | humanizeDuration }} ago.
      All VaR figures in the UI are potentially wrong.
      Escalate immediately and investigate price feed, consumer lag, and Redis health.
```

---

## PrometheusRule Resources

Deploy the rules as `PrometheusRule` custom resources so the Prometheus operator picks them up automatically. One resource per subsystem.

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: kinetix-market-data-alerts
  namespace: kinetix
  labels:
    release: kube-prometheus-stack  # matches the Prometheus operator selector
spec:
  groups:
    - name: kinetix.market-data.price-feed
      interval: 60s
      rules:
        - alert: MarketDataPriceFeedStale
          # ... (rule body as above)
        - alert: MarketDataPriceFeedStaleCritical
          # ...

    - name: kinetix.market-data.consumer-lag
      interval: 60s
      rules:
        - alert: MarketDataConsumerLagHigh
          # ...
        - alert: MarketDataConsumerLagCritical
          # ...

    - name: kinetix.market-data.var-cache
      interval: 60s
      rules:
        - alert: VaRCacheStale
          # ...
        - alert: VaRCacheStaleCritical
          # ...
```

Place the `PrometheusRule` manifests in `deploy/helm/kinetix/templates/prometheus-rules/`.

---

## Escalation Levels

| Severity | Alert | Response time | Action |
|---|---|---|---|
| Warning | Price feed stale >5min | 15 minutes | Investigate price-service consumer, check upstream feed connectivity |
| Warning | Consumer lag >10k | 15 minutes | Check consumer pod health, scale if needed, inspect DLQ |
| Warning | VaR cache stale >10min | 15 minutes | Check risk-orchestrator, confirm price feed and consumer are healthy |
| Critical | Price feed stale >10min | Immediate (page) | Escalate to on-call, notify risk management, consider suspending live trading |
| Critical | Consumer lag >20k | Immediate (page) | Scale consumers, investigate backpressure source, check for poison messages in DLQ |
| Critical | VaR cache stale >20min | Immediate (page) | Escalate to on-call, notify risk management, mark risk dashboard as unreliable |

---

## Integration with notification-service

For critical alerts during market hours, route Alertmanager notifications to the notification-service WebSocket endpoint so traders see the degradation in the UI without polling the Grafana dashboard.

The notification-service already has an `alert_rules` table and an alert evaluation loop. A new alert category — `MARKET_DATA_DEGRADED` — can be added to carry the feed-staleness and cache-age signals without requiring a new Kafka topic. The Alertmanager webhook receiver calls the notification-service `/internal/alerts` endpoint, which creates an `AlertEvent` that propagates to connected WebSocket clients.

This integration is a follow-on task; the Prometheus alerting rules above can be deployed independently and are valuable on their own via Grafana alerting.
