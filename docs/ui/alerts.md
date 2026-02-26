# Alerts Tab — Threshold Monitoring & Notifications

The Alerts tab provides a rules-based alerting system that monitors risk metrics in real time and notifies traders and risk managers when thresholds are breached.

---

## What it displays

### Create Alert Rule Form

Allows users to define new monitoring rules with:
- **Rule name** — descriptive label
- **Rule type** — `VAR_BREACH`, `PNL_THRESHOLD`, or `RISK_LIMIT`
- **Threshold** — numeric trigger value
- **Operator** — `GREATER_THAN`, `LESS_THAN`, or `EQUALS`
- **Severity** — `CRITICAL` (red), `WARNING` (yellow), or `INFO` (blue)
- **Delivery channels** — multi-select: `IN_APP`, `EMAIL`, `WEBHOOK`

### Alert Rules Table

Lists all configured rules with columns: Name, Type, Threshold, Severity (colored badge), Enabled status, and a Delete button.

### Recent Alerts

Displays triggered alert events sorted by recency, then severity:
- **Severity icon** — AlertCircle (CRITICAL), AlertTriangle (WARNING), Info (INFO)
- **Color-coded border** — red, yellow, or blue
- **Alert message** — describes what triggered and at what value
- **Portfolio ID** — which portfolio triggered the alert
- **Relative time** — e.g. "2h ago"

An **alert count badge** appears on the tab header when alerts exist.

---

## Alert types

| Type | What it monitors | Input metric |
|------|-----------------|-------------|
| **VAR_BREACH** | VaR exceeds a limit | `varValue` from risk calculation |
| **PNL_THRESHOLD** | P&L crosses a threshold | `expectedShortfall` from risk calculation |
| **RISK_LIMIT** | General risk limit monitoring | `varValue` from risk calculation |

---

## How alerts flow through the system

```
Risk Calculation completes
  → Publishes RiskResultEvent to Kafka "risk.results"
    → Notification Service (RiskResultConsumer) receives event
      → RulesEngine.evaluate(event)
        1. Load all enabled rules
        2. Extract current value based on alert type
        3. Compare against threshold using operator
        4. If triggered → create AlertEvent
      → DeliveryRouter routes to channel-specific services:
        ├── IN_APP  → persists to alert_events table → UI polls to display
        ├── EMAIL   → sends email notification (stub)
        └── WEBHOOK → sends webhook payload (stub)
```

### Anomaly detection

A second consumer listens to `risk.anomalies` for ML-detected anomalies:
- `AnomalyEventConsumer` subscribes to `risk.anomalies` topic
- Receives `AnomalyEvent` with anomalyScore and explanation
- Currently logged; can be wired into the rules engine

---

## Why a trader / investment bank needs this

1. **Proactive risk management** — Instead of manually watching dashboards, the system alerts you the moment a VaR limit is breached.
2. **Regulatory compliance** — Regulators require banks to have automated breach detection and escalation for risk limits.
3. **Tiered severity** — CRITICAL alerts for immediate action (VaR breach), WARNING for attention needed (approaching limits), INFO for awareness.
4. **Multi-channel delivery** — In-app for the trading desk, email for management, webhooks for integration with Slack/PagerDuty/other systems.
5. **Customisable rules** — Each desk can define their own thresholds and operators to match their risk appetite and mandate.
6. **Audit trail** — Every triggered alert is persisted with timestamp, current value, threshold, and portfolio, creating a compliance record.

---

## Architecture

```
Kafka "risk.results"
  → RiskResultConsumer (notification-service)
    → RulesEngine (evaluates all enabled rules)
      → DeliveryRouter
        ├── InAppDeliveryService  → PostgreSQL alert_events table
        ├── EmailDeliveryService  → email (stub)
        └── WebhookDeliveryService → HTTP webhook (stub)

UI (NotificationCenter)
  → GET /api/v1/notifications/alerts  → InAppDeliveryService.getRecentAlerts()
  → GET /api/v1/notifications/rules   → AlertRuleRepository.findAll()
  → POST /api/v1/notifications/rules  → AlertRuleRepository.save()
  → DELETE /api/v1/notifications/rules/{id} → AlertRuleRepository.deleteById()
```

---

## Key files

| Component | Location |
|-----------|----------|
| UI Component | `ui/src/components/NotificationCenter.tsx` |
| Notifications Hook | `ui/src/hooks/useNotifications.ts` |
| API Client | `ui/src/api/notifications.ts` |
| Rules Engine | `notification-service/src/main/kotlin/com/kinetix/notification/engine/RulesEngine.kt` |
| Delivery Router | `notification-service/src/main/kotlin/com/kinetix/notification/delivery/DeliveryRouter.kt` |
| In-App Delivery | `notification-service/src/main/kotlin/com/kinetix/notification/delivery/InAppDeliveryService.kt` |
| Email Delivery | `notification-service/src/main/kotlin/com/kinetix/notification/delivery/EmailDeliveryService.kt` |
| Webhook Delivery | `notification-service/src/main/kotlin/com/kinetix/notification/delivery/WebhookDeliveryService.kt` |
| Risk Result Consumer | `notification-service/src/main/kotlin/com/kinetix/notification/kafka/RiskResultConsumer.kt` |
| Anomaly Consumer | `notification-service/src/main/kotlin/com/kinetix/notification/kafka/AnomalyEventConsumer.kt` |
| Domain Models | `notification-service/src/main/kotlin/com/kinetix/notification/model/AlertModels.kt` |
| Alert Rules Repository | `notification-service/src/main/kotlin/com/kinetix/notification/persistence/ExposedAlertRuleRepository.kt` |
| Alert Events Repository | `notification-service/src/main/kotlin/com/kinetix/notification/persistence/ExposedAlertEventRepository.kt` |
| Dev Data Seeder | `notification-service/src/main/kotlin/com/kinetix/notification/seed/DevDataSeeder.kt` |

---

## API Endpoints

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/v1/notifications/rules` | GET | List all alert rules |
| `/api/v1/notifications/rules` | POST | Create a new alert rule |
| `/api/v1/notifications/rules/{ruleId}` | DELETE | Delete an alert rule |
| `/api/v1/notifications/alerts` | GET | List recent alert events (default limit: 50) |

---

## Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `risk.results` | Consumed by Notification Service | Risk calculation results that trigger rule evaluation |
| `risk.anomalies` | Consumed by Notification Service | ML-detected anomaly events |

---

## Database Schema

### alert_rules table
```
id         VARCHAR(255)    PK
name       VARCHAR(255)
type       VARCHAR(50)     -- VAR_BREACH, PNL_THRESHOLD, RISK_LIMIT
threshold  NUMERIC(28,12)
operator   VARCHAR(50)     -- GREATER_THAN, LESS_THAN, EQUALS
severity   VARCHAR(50)     -- CRITICAL, WARNING, INFO
channels   VARCHAR(255)    -- comma-separated: IN_APP,EMAIL,WEBHOOK
enabled    BOOLEAN
```

### alert_events table
```
id            VARCHAR(255)    PK
rule_id       VARCHAR(255)    indexed
rule_name     VARCHAR(255)
type          VARCHAR(50)
severity      VARCHAR(50)
message       TEXT
current_value NUMERIC(28,12)
threshold     NUMERIC(28,12)
portfolio_id  VARCHAR(255)    indexed
triggered_at  TIMESTAMPTZ     indexed DESC
```
