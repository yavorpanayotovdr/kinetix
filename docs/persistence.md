# Persistence Layer

This document is the single reference for every database, table, repository, and connection-pool setting in the Kinetix platform. It also describes how data flows between services via Kafka (no service shares a database).

---

## Overview

| Concern | Technology |
|---------|------------|
| RDBMS | PostgreSQL 17 (TimescaleDB image) |
| Time-series | TimescaleDB hypertable (Price Service only) |
| ORM | Exposed 0.58.0 (core, dao, jdbc, kotlin-datetime, json) |
| Migrations | Flyway 11.3.1 (core + postgresql module) |
| Connection pool | HikariCP 6.2.1 |
| JDBC driver | PostgreSQL JDBC 42.7.5 |

Every microservice owns its own database — there is no shared schema. Schema evolution is managed by Flyway migrations under each service's `src/main/resources/db/<service>/` directory. Data access goes through the repository pattern: an interface defines the contract, and an `Exposed*Repository` implementation provides the SQL via Exposed's type-safe DSL inside `newSuspendedTransaction` blocks.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            PostgreSQL (TimescaleDB)                              │
│                                                                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │kinetix_      │ │kinetix_      │ │kinetix_      │ │kinetix_      │           │
│  │position      │ │price         │ │risk          │ │audit         │           │
│  │              │ │ (hypertable) │ │              │ │              │           │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘           │
│         │                │                │                │                    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐           │
│  │kinetix_      │ │kinetix_      │ │kinetix_      │ │kinetix_      │           │
│  │notification  │ │regulatory    │ │rates         │ │reference_data│           │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘           │
│         │                │                │                │                    │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                            │
│  │kinetix_      │ │kinetix_      │ │kinetix_      │                            │
│  │volatility    │ │correlation   │ │gateway       │                            │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘                            │
└─────────┼───────────────┼───────────────┼──────────────────────────────────────┘
          │               │               │
          ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               Kafka Topics                                      │
│                                                                                 │
│  trades.lifecycle   price.updates   risk.results   risk.anomalies               │
│  rates.yield-curves   rates.risk-free   rates.forwards                          │
│  reference-data.dividends   reference-data.credit-spreads                       │
│  volatility.surfaces   correlation.matrices                                     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Per-Service Persistence

### 1. Position Service

| Property | Value |
|----------|-------|
| Database | `kinetix_position` |
| Migration path | `db/position-service/` |
| Pool | maxPoolSize=15, minIdle=3 |

#### Tables

**trade_events** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| trade_id | VARCHAR | **PK** |
| portfolio_id | VARCHAR | Indexed |
| instrument_id | VARCHAR | |
| asset_class | VARCHAR | |
| side | VARCHAR | |
| quantity | DECIMAL(28,12) | |
| price_amount | DECIMAL(28,12) | |
| price_currency | VARCHAR | |
| traded_at | TIMESTAMPTZ | Indexed |
| created_at | TIMESTAMPTZ | |

**positions** (V2)

| Column | Type | Constraints |
|--------|------|-------------|
| portfolio_id | VARCHAR | **PK** (composite) |
| instrument_id | VARCHAR | **PK** (composite) |
| asset_class | VARCHAR | |
| quantity | DECIMAL(28,12) | |
| avg_cost_amount | DECIMAL(28,12) | |
| market_price_amount | DECIMAL(28,12) | |
| currency | VARCHAR | |
| updated_at | TIMESTAMPTZ | |

#### Repository — `TradeEventRepository`

```kotlin
suspend fun save(trade: Trade)
suspend fun findByTradeId(tradeId: TradeId): Trade?
suspend fun findByPortfolioId(portfolioId: PortfolioId): List<Trade>
```

#### Repository — `PositionRepository`

```kotlin
suspend fun save(position: Position)
suspend fun findByPortfolioId(portfolioId: PortfolioId): List<Position>
suspend fun findByKey(portfolioId: PortfolioId, instrumentId: InstrumentId): Position?
suspend fun findByInstrumentId(instrumentId: InstrumentId): List<Position>
suspend fun delete(portfolioId: PortfolioId, instrumentId: InstrumentId)
suspend fun findDistinctPortfolioIds(): List<PortfolioId>
```

---

### 2. Price Service

| Property | Value |
|----------|-------|
| Database | `kinetix_price` (TimescaleDB extension enabled) |
| Migration path | `db/price-service/` |
| Pool | maxPoolSize=20, minIdle=5 |

#### Tables

**prices** (V1 as `market_data`, renamed V2)

| Column | Type | Constraints |
|--------|------|-------------|
| instrument_id | VARCHAR | **PK** (composite) |
| timestamp | TIMESTAMPTZ | **PK** (composite) |
| price_amount | DECIMAL(28,12) | |
| price_currency | VARCHAR | |
| data_source | VARCHAR | |
| created_at | TIMESTAMPTZ | |

The table is a TimescaleDB hypertable partitioned on `timestamp`. An index on `instrument_id` supports instrument-scoped queries.

#### Repository — `PriceRepository`

```kotlin
suspend fun save(point: PricePoint)
suspend fun findLatest(instrumentId: InstrumentId): PricePoint?
suspend fun findByInstrumentId(instrumentId: InstrumentId, from: Instant, to: Instant): List<PricePoint>
```

---

### 3. Risk Orchestrator

| Property | Value |
|----------|-------|
| Database | `kinetix_risk` |
| Migration path | `db/risk-orchestrator/` |
| Pool | maxPoolSize=8, minIdle=2 |

#### Tables

**valuation_jobs** (V1 as `calculation_runs`, V2 → `calculation_jobs`, V3 → `valuation_jobs`, V4 adds `pv_value`)

| Column | Type | Constraints |
|--------|------|-------------|
| job_id | UUID | **PK** |
| portfolio_id | VARCHAR | Indexed (composite with started_at) |
| trigger_type | VARCHAR | |
| status | VARCHAR | |
| started_at | TIMESTAMPTZ | Indexed (composite with portfolio_id) |
| completed_at | TIMESTAMPTZ | Nullable |
| duration_ms | BIGINT | Nullable |
| calculation_type | VARCHAR | Nullable |
| confidence_level | DOUBLE PRECISION | Nullable |
| var_value | DOUBLE PRECISION | Nullable |
| expected_shortfall | DOUBLE PRECISION | Nullable |
| pv_value | DOUBLE PRECISION | Nullable |
| steps | JSONB | Nullable — `List<JobStepJson>` |
| error | TEXT | Nullable |

#### Repository — `ValuationJobRepository`

Tracks the lifecycle of every VaR/Greeks calculation run, including per-step timing stored as JSONB.

---

### 4. Audit Service

| Property | Value |
|----------|-------|
| Database | `kinetix_audit` |
| Migration path | `db/audit-service/` |
| Pool | maxPoolSize=8, minIdle=2 |

#### Tables

**audit_events** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | **PK** (auto-increment) |
| trade_id | VARCHAR | Indexed |
| portfolio_id | VARCHAR | Indexed |
| instrument_id | VARCHAR | |
| asset_class | VARCHAR | |
| side | VARCHAR | |
| quantity | VARCHAR | |
| price_amount | VARCHAR | |
| price_currency | VARCHAR | |
| traded_at | VARCHAR | |
| received_at | TIMESTAMPTZ | |

All trade data is stored as VARCHAR for audit immutability — values are preserved exactly as received.

#### Repository — `AuditEventRepository`

```kotlin
suspend fun save(event: AuditEvent)
suspend fun findAll(): List<AuditEvent>
suspend fun findByPortfolioId(portfolioId: String): List<AuditEvent>
```

---

### 5. Notification Service

| Property | Value |
|----------|-------|
| Database | `kinetix_notification` |
| Migration path | `db/notification-service/` |
| Pool | maxPoolSize=8, minIdle=2 |

#### Tables

**alert_rules** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR | **PK** |
| name | VARCHAR | |
| type | VARCHAR | |
| threshold | DOUBLE PRECISION | |
| operator | VARCHAR | |
| severity | VARCHAR | |
| channels | VARCHAR | CSV string (e.g. `"EMAIL,WEBHOOK"`) |
| enabled | BOOLEAN | |

**alert_events** (V2)

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR | **PK** |
| rule_id | VARCHAR | Indexed |
| rule_name | VARCHAR | |
| type | VARCHAR | |
| severity | VARCHAR | |
| message | TEXT | |
| current_value | DOUBLE PRECISION | |
| threshold | DOUBLE PRECISION | |
| portfolio_id | VARCHAR | Indexed |
| triggered_at | TIMESTAMPTZ | Indexed (DESC) |

#### Repository — `AlertRuleRepository`

```kotlin
suspend fun save(rule: AlertRule)
suspend fun findAll(): List<AlertRule>
suspend fun deleteById(id: String): Boolean
```

#### Repository — `AlertEventRepository`

```kotlin
suspend fun save(event: AlertEvent)
suspend fun findRecent(limit: Int = 50): List<AlertEvent>
```

---

### 6. Regulatory Service

| Property | Value |
|----------|-------|
| Database | `kinetix_regulatory` |
| Migration path | `db/regulatory-service/` |
| Pool | maxPoolSize=8, minIdle=2 |

#### Tables

**frtb_calculations** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| id | VARCHAR(255) | **PK** |
| portfolio_id | VARCHAR | Indexed |
| total_sbm_charge | DOUBLE PRECISION | |
| gross_jtd | DOUBLE PRECISION | |
| hedge_benefit | DOUBLE PRECISION | |
| net_drc | DOUBLE PRECISION | |
| exotic_notional | DOUBLE PRECISION | |
| other_notional | DOUBLE PRECISION | |
| total_rrao | DOUBLE PRECISION | |
| total_capital_charge | DOUBLE PRECISION | |
| sbm_charges_json | TEXT | JSON detail by bucket |
| calculated_at | TIMESTAMPTZ | Indexed (DESC) |
| stored_at | TIMESTAMPTZ | |

#### Repository — `FrtbCalculationRepository`

```kotlin
suspend fun save(record: FrtbCalculationRecord)
suspend fun findByPortfolioId(portfolioId: String, limit: Int, offset: Int): List<FrtbCalculationRecord>
suspend fun findLatestByPortfolioId(portfolioId: String): FrtbCalculationRecord?
```

---

### 7. Rates Service

| Property | Value |
|----------|-------|
| Database | `kinetix_rates` |
| Migration path | `db/rates-service/` |
| Pool | maxPoolSize=10, minIdle=3 |

#### Tables

**yield_curves** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| curve_id | VARCHAR | **PK** (composite) |
| as_of_date | TIMESTAMPTZ | **PK** (composite) |
| currency | VARCHAR | |
| data_source | VARCHAR | |
| created_at | TIMESTAMPTZ | |

**yield_curve_tenors** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| curve_id | VARCHAR | **PK** (composite), FK → yield_curves (CASCADE) |
| as_of_date | TIMESTAMPTZ | **PK** (composite), FK → yield_curves (CASCADE) |
| label | VARCHAR | **PK** (composite) |
| days | INTEGER | |
| rate | DECIMAL(28,12) | |

**risk_free_rates** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| currency | VARCHAR | **PK** (composite) |
| tenor | VARCHAR | **PK** (composite) |
| as_of_date | TIMESTAMPTZ | **PK** (composite) |
| rate | DECIMAL(28,12) | |
| data_source | VARCHAR | |
| created_at | TIMESTAMPTZ | |

**forward_curves** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| instrument_id | VARCHAR | **PK** (composite) |
| as_of_date | TIMESTAMPTZ | **PK** (composite) |
| asset_class | VARCHAR | |
| data_source | VARCHAR | |
| created_at | TIMESTAMPTZ | |

**forward_curve_points** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| instrument_id | VARCHAR | **PK** (composite), FK → forward_curves (CASCADE) |
| as_of_date | TIMESTAMPTZ | **PK** (composite), FK → forward_curves (CASCADE) |
| tenor | VARCHAR | **PK** (composite) |
| value | DECIMAL(28,12) | |

#### Repository — `YieldCurveRepository`

```kotlin
suspend fun save(curve: YieldCurve)
suspend fun findLatest(curveId: String): YieldCurve?
suspend fun findByTimeRange(curveId: String, from: Instant, to: Instant): List<YieldCurve>
```

#### Repository — `RiskFreeRateRepository`

```kotlin
suspend fun save(rate: RiskFreeRate)
suspend fun findLatest(currency: Currency, tenor: String): RiskFreeRate?
suspend fun findByTimeRange(currency: Currency, tenor: String, from: Instant, to: Instant): List<RiskFreeRate>
```

#### Repository — `ForwardCurveRepository`

```kotlin
suspend fun save(curve: ForwardCurve)
suspend fun findLatest(instrumentId: InstrumentId): ForwardCurve?
suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<ForwardCurve>
```

---

### 8. Reference Data Service

| Property | Value |
|----------|-------|
| Database | `kinetix_reference_data` |
| Migration path | `db/reference-data-service/` |
| Pool | maxPoolSize=8, minIdle=2 |

#### Tables

**dividend_yields** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| instrument_id | VARCHAR | **PK** (composite) |
| as_of_date | TIMESTAMPTZ | **PK** (composite) |
| yield | DECIMAL(18,8) | |
| ex_date | VARCHAR(10) | Nullable |
| data_source | VARCHAR | |
| created_at | TIMESTAMPTZ | |

**credit_spreads** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| instrument_id | VARCHAR | **PK** (composite) |
| as_of_date | TIMESTAMPTZ | **PK** (composite) |
| spread | DECIMAL(18,8) | |
| rating | VARCHAR(20) | Nullable |
| data_source | VARCHAR | |
| created_at | TIMESTAMPTZ | |

#### Repository — `DividendYieldRepository`

```kotlin
suspend fun save(dividendYield: DividendYield)
suspend fun findLatest(instrumentId: InstrumentId): DividendYield?
suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<DividendYield>
```

#### Repository — `CreditSpreadRepository`

```kotlin
suspend fun save(creditSpread: CreditSpread)
suspend fun findLatest(instrumentId: InstrumentId): CreditSpread?
suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<CreditSpread>
```

---

### 9. Volatility Service

| Property | Value |
|----------|-------|
| Database | `kinetix_volatility` |
| Migration path | `db/volatility-service/` |
| Pool | maxPoolSize=10, minIdle=3 |

#### Tables

**volatility_surfaces** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| instrument_id | VARCHAR | **PK** (composite) |
| as_of_date | TIMESTAMPTZ | **PK** (composite) |
| data_source | VARCHAR | |
| created_at | TIMESTAMPTZ | |

**volatility_surface_points** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| instrument_id | VARCHAR | **PK** (composite), FK → volatility_surfaces |
| as_of_date | TIMESTAMPTZ | **PK** (composite), FK → volatility_surfaces |
| strike | DECIMAL(28,12) | **PK** (composite) |
| maturity_days | INTEGER | **PK** (composite) |
| implied_vol | DECIMAL(18,8) | |

#### Repository — `VolSurfaceRepository`

```kotlin
suspend fun save(surface: VolSurface)
suspend fun findLatest(instrumentId: InstrumentId): VolSurface?
suspend fun findByTimeRange(instrumentId: InstrumentId, from: Instant, to: Instant): List<VolSurface>
```

---

### 10. Correlation Service

| Property | Value |
|----------|-------|
| Database | `kinetix_correlation` |
| Migration path | `db/correlation-service/` |
| Pool | maxPoolSize=8, minIdle=2 |

#### Tables

**correlation_matrices** (V1)

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | **PK** (auto-increment) |
| labels | TEXT | JSON array of instrument labels |
| values | TEXT | JSON 2-D matrix |
| window_days | INTEGER | |
| as_of_date | TIMESTAMPTZ | Indexed |
| method | VARCHAR | |
| created_at | TIMESTAMPTZ | |

Indexed on `(labels, window_days)` for latest-matrix lookups and on `as_of_date` for time-range queries.

#### Repository — `CorrelationMatrixRepository`

```kotlin
suspend fun save(matrix: CorrelationMatrix)
suspend fun findLatest(labels: List<String>, windowDays: Int): CorrelationMatrix?
suspend fun findByTimeRange(labels: List<String>, windowDays: Int, from: Instant, to: Instant): List<CorrelationMatrix>
```

---

### 11. Gateway

| Property | Value |
|----------|-------|
| Database | `kinetix_gateway` |
| Migration path | — (no migrations) |

The gateway database is created by the init script but currently has no tables — the gateway is a stateless reverse-proxy.

---

## Cross-Service Data Flow

No service reads another service's database. All inter-service data propagation happens through Kafka topics.

### Kafka Topics

| Topic | Event | Publisher | Consumers |
|-------|-------|-----------|-----------|
| `price.updates` | PriceEvent | Price Service | Position Service, Risk Orchestrator |
| `trades.lifecycle` | TradeEvent | Position Service | Risk Orchestrator, Audit Service |
| `risk.results` | RiskResultEvent | Risk Orchestrator | Notification Service |
| `risk.anomalies` | AnomalyEvent | — | Notification Service |
| `rates.yield-curves` | YieldCurveEvent | Rates Service | — |
| `rates.risk-free` | RiskFreeRateEvent | Rates Service | — |
| `rates.forwards` | ForwardCurveEvent | Rates Service | — |
| `reference-data.dividends` | DividendYieldEvent | Reference Data Service | — |
| `reference-data.credit-spreads` | CreditSpreadEvent | Reference Data Service | — |
| `volatility.surfaces` | VolSurfaceEvent | Volatility Service | — |
| `correlation.matrices` | CorrelationMatrixEvent | Correlation Service | — |

Market-data topics (rates, reference data, volatility, correlation) are published for downstream consumers. The Risk Orchestrator fetches this data via HTTP REST APIs rather than Kafka consumers.

### Consumer Groups

| Group ID | Service | Topic |
|----------|---------|-------|
| `position-service-group` | Position Service | `price.updates` |
| `risk-orchestrator-trades-group` | Risk Orchestrator | `trades.lifecycle` |
| `risk-orchestrator-prices-group` | Risk Orchestrator | `price.updates` |
| `notification-service-risk-group` | Notification Service | `risk.results` |
| `notification-service-anomaly-group` | Notification Service | `risk.anomalies` |
| `audit-service-group` | Audit Service | `trades.lifecycle` |

All consumer groups use `auto.offset.reset = earliest`.

### Primary Data Flows

**Trade → VaR → Alert:**

```
Position Service ──trades.lifecycle──▶ Risk Orchestrator ──risk.results──▶ Notification Service
                                      Risk Orchestrator ──risk.results──▶ Notification Service
Position Service ──trades.lifecycle──▶ Audit Service (persist)
```

**Price tick → Position update → VaR recalculation:**

```
Price Service ──price.updates──▶ Position Service (update market price)
Price Service ──price.updates──▶ Risk Orchestrator (recalculate VaR for affected portfolios)
```

---

## Common Patterns

### DatabaseFactory

Every Kotlin service initialises its database with a `DatabaseFactory` that:

1. Creates a `HikariDataSource` with the pool config returned by `ConnectionPoolConfig.forService("<name>")`.
2. Runs Flyway migrations against that datasource (`classpath:db/<service-name>`).
3. Connects Exposed via `Database.connect(dataSource)`.

```kotlin
data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val poolConfig: ConnectionPoolConfig = ConnectionPoolConfig.forService("position-service"),
)

object DatabaseFactory {
    fun init(config: DatabaseConfig): Database {
        val dataSource = createDataSource(config)
        runMigrations(dataSource)
        return Database.connect(dataSource)
    }
}
```

### Connection Pool Defaults

```kotlin
data class ConnectionPoolConfig(
    val maxPoolSize: Int = 10,
    val minIdle: Int = 2,
    val connectionTimeout: Long = 30_000,   // 30 s
    val idleTimeout: Long = 600_000,        // 10 min
    val maxLifetime: Long = 1_800_000,      // 30 min
    val leakDetectionThreshold: Long = 60_000, // 1 min
    val transactionIsolation: String = "TRANSACTION_REPEATABLE_READ",
    val isAutoCommit: Boolean = false,
)
```

Service-specific overrides:

| Service | maxPoolSize | minIdle |
|---------|-------------|---------|
| position-service | 15 | 3 |
| price-service | 20 | 5 |
| rates-service | 10 | 3 |
| volatility-service | 10 | 3 |
| audit-service | 8 | 2 |
| notification-service | 8 | 2 |
| regulatory-service | 8 | 2 |
| reference-data-service | 8 | 2 |
| correlation-service | 8 | 2 |
| risk-orchestrator | 8 | 2 |

### Repository Pattern

Each service defines a repository interface in its domain layer and an `Exposed*Repository` implementation in its persistence layer. All database calls are wrapped in `newSuspendedTransaction` for coroutine compatibility.

```kotlin
// Interface — domain layer
interface TradeEventRepository {
    suspend fun save(trade: Trade)
    suspend fun findByTradeId(tradeId: TradeId): Trade?
}

// Implementation — persistence layer
class ExposedTradeEventRepository(private val db: Database) : TradeEventRepository {
    override suspend fun save(trade: Trade) = newSuspendedTransaction(db = db) {
        TradeEventsTable.insert { /* … */ }
    }
}
```

### Flyway Migration Naming

Migrations follow the standard Flyway convention: `V<number>__<description>.sql` inside `src/main/resources/db/<service-name>/`. Each service's `DatabaseFactory` points Flyway at `classpath:db/<service-name>`.

### Database Initialisation Script

`infra/db/init/01-create-databases.sql` runs once when the PostgreSQL container starts. It creates all 11 per-service databases using idempotent `SELECT 'CREATE DATABASE …'` blocks and enables the TimescaleDB extension on `kinetix_price`.

---

## Type Conventions

### Exposed → PostgreSQL Mapping

| Exposed Column API | PostgreSQL Type | Used For |
|--------------------|-----------------|----------|
| `varchar(…)` | VARCHAR | IDs, enums, currencies |
| `decimal(28, 12)` | DECIMAL(28,12) | Financial amounts (prices, quantities) |
| `decimal(18, 8)` | DECIMAL(18,8) | Rates, yields, spreads, implied vols |
| `double(…)` | DOUBLE PRECISION | Capital charges, thresholds, VaR values |
| `integer(…)` | INTEGER | Day counts, window sizes |
| `long(…)` / `BIGSERIAL` | BIGINT / BIGSERIAL | Auto-increment PKs |
| `bool(…)` | BOOLEAN | Flags (e.g. `enabled`) |
| `text(…)` | TEXT | Long strings, JSON blobs, error messages |
| `jsonb(…)` | JSONB | Structured nested data (valuation steps) |
| `timestampWithTimeZone(…)` | TIMESTAMPTZ | Event timestamps and as-of dates (stored at UTC) |
| `uuid(…)` | UUID | Job identifiers |

### Timestamp Strategy

All `TIMESTAMPTZ` columns store UTC values. The Exposed repository implementations convert between `kotlinx.datetime.Instant` and `java.time.OffsetDateTime` at UTC for database operations.
