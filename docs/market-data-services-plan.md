# Market Data Services Plan

## Context

The risk engine's dependency registry declares what market data each asset class needs for VaR calculation. Today only **SPOT_PRICE** and **HISTORICAL_PRICES** are fetchable (both from price-service). The remaining seven types are discovered but silently skipped.

| Asset Class | Required | Optional |
|---|---|---|
| EQUITY | SPOT_PRICE, HISTORICAL_PRICES | — |
| FIXED_INCOME | YIELD_CURVE, CREDIT_SPREAD | — |
| FX | SPOT_PRICE | FORWARD_CURVE |
| COMMODITY | SPOT_PRICE | FORWARD_CURVE |
| DERIVATIVE | SPOT_PRICE, VOLATILITY_SURFACE, RISK_FREE_RATE | DIVIDEND_YIELD |
| Portfolio-level | CORRELATION_MATRIX (if 2+ asset classes) | — |

## PriceService reference architecture (what we're replicating)

| Layer | price-service example |
|---|---|
| **HTTP routes** | `GET /api/v1/prices/{id}/latest`, `.../history` |
| **Service** | `PriceIngestionService` — save → cache → publish |
| **Repository** | `PriceRepository` interface → `ExposedPriceRepository` (Postgres) |
| **Cache** | `PriceCache` interface → `RedisPriceCache` |
| **Kafka publisher** | `PricePublisher` → `KafkaPricePublisher` (topic: `price.updates`) |
| **Domain model** | `PricePoint` (in `common`) |
| **Client (in risk-orchestrator)** | `PriceServiceClient` interface → `HttpPriceServiceClient` |
| **MarketDataFetcher case** | `SPOT_PRICE` → `ScalarMarketData`, `HISTORICAL_PRICES` → `TimeSeriesMarketData` |

---

## Service 1: rates-service

**Covers:** YIELD_CURVE, RISK_FREE_RATE, FORWARD_CURVE

### Domain models (in `common`)

```kotlin
data class CurvePoint(val tenor: String, val value: Double)

data class YieldCurve(
    val curveId: String,
    val currency: Currency,
    val points: List<CurvePoint>,
    val asOfDate: Instant,
    val source: RateSource,
)

data class RiskFreeRate(
    val currency: Currency,
    val tenor: String,
    val rate: Double,
    val asOfDate: Instant,
    val source: RateSource,
)

data class ForwardCurve(
    val instrumentId: InstrumentId,
    val assetClass: String,
    val points: List<CurvePoint>,
    val asOfDate: Instant,
    val source: RateSource,
)

enum class RateSource { BLOOMBERG, REUTERS, CENTRAL_BANK, INTERNAL, MANUAL }
```

### HTTP endpoints

| Endpoint | Returns |
|---|---|
| `GET /api/v1/rates/yield-curves/{curveId}/latest` | Latest `YieldCurve` |
| `GET /api/v1/rates/yield-curves/{curveId}/history?from=&to=` | Historical snapshots |
| `GET /api/v1/rates/risk-free/{currency}/latest?tenor=` | Latest `RiskFreeRate` |
| `GET /api/v1/rates/forwards/{instrumentId}/latest` | Latest `ForwardCurve` |
| `GET /api/v1/rates/forwards/{instrumentId}/history?from=&to=` | Historical snapshots |

### Persistence

- `yield_curves` table: `(curve_id, as_of_date)` PK, `currency`, `source`, `created_at`
- `yield_curve_points` table: `(curve_id, as_of_date, tenor)` PK, `value`
- `risk_free_rates` table: `(currency, tenor, as_of_date)` PK, `rate`, `source`
- `forward_curves` table: `(instrument_id, as_of_date)` PK, `asset_class`, `source`
- `forward_curve_points` table: `(instrument_id, as_of_date, tenor)` PK, `value`

### Kafka topics

`rates.yield-curves`, `rates.risk-free`, `rates.forwards`

### Client in risk-orchestrator

```kotlin
interface RatesServiceClient {
    suspend fun getLatestYieldCurve(curveId: String): YieldCurve?
    suspend fun getLatestRiskFreeRate(currency: Currency, tenor: String): RiskFreeRate?
    suspend fun getLatestForwardCurve(instrumentId: InstrumentId): ForwardCurve?
}
```

### MarketDataFetcher cases

- `YIELD_CURVE` → `CurveMarketData` (new)
- `RISK_FREE_RATE` → `ScalarMarketData` (existing)
- `FORWARD_CURVE` → `CurveMarketData` (new)

---

## Service 2: reference-data-service

**Covers:** DIVIDEND_YIELD, CREDIT_SPREAD

### Domain models (in `common`)

```kotlin
data class DividendYield(
    val instrumentId: InstrumentId,
    val yield: Double,
    val exDate: LocalDate?,
    val asOfDate: Instant,
    val source: ReferenceDataSource,
)

data class CreditSpread(
    val instrumentId: InstrumentId,
    val spread: Double,
    val rating: String?,
    val asOfDate: Instant,
    val source: ReferenceDataSource,
)

enum class ReferenceDataSource { BLOOMBERG, REUTERS, RATING_AGENCY, INTERNAL, MANUAL }
```

### HTTP endpoints

| Endpoint | Returns |
|---|---|
| `GET /api/v1/reference-data/dividends/{instrumentId}/latest` | Latest `DividendYield` |
| `GET /api/v1/reference-data/dividends/{instrumentId}/history?from=&to=` | Historical |
| `GET /api/v1/reference-data/credit-spreads/{instrumentId}/latest` | Latest `CreditSpread` |
| `GET /api/v1/reference-data/credit-spreads/{instrumentId}/history?from=&to=` | Historical |

### Persistence

- `dividend_yields` table: `(instrument_id, as_of_date)` PK, `yield`, `ex_date`, `source`
- `credit_spreads` table: `(instrument_id, as_of_date)` PK, `spread`, `rating`, `source`

### Kafka topics

`reference-data.dividends`, `reference-data.credit-spreads`

### Client in risk-orchestrator

```kotlin
interface ReferenceDataServiceClient {
    suspend fun getLatestDividendYield(instrumentId: InstrumentId): DividendYield?
    suspend fun getLatestCreditSpread(instrumentId: InstrumentId): CreditSpread?
}
```

### MarketDataFetcher cases

- `DIVIDEND_YIELD` → `ScalarMarketData` (existing)
- `CREDIT_SPREAD` → `ScalarMarketData` (existing)

---

## Service 3: volatility-service

**Covers:** VOLATILITY_SURFACE

### Domain models (in `common`)

```kotlin
data class VolatilitySurface(
    val instrumentId: InstrumentId,
    val strikes: List<Double>,
    val expiries: List<String>,
    val values: List<Double>,       // row-major (expiry x strike)
    val asOfDate: Instant,
    val source: VolatilitySource,
)

enum class VolatilitySource { BLOOMBERG, REUTERS, EXCHANGE, INTERNAL, MANUAL }
```

### HTTP endpoints

| Endpoint | Returns |
|---|---|
| `GET /api/v1/volatility/{instrumentId}/surface/latest` | Latest `VolatilitySurface` |
| `GET /api/v1/volatility/{instrumentId}/surface/history?from=&to=` | Historical snapshots |
| `GET /api/v1/volatility/{instrumentId}/atm?expiry=` | Single ATM vol |

### Persistence

- `volatility_surfaces` table: `(instrument_id, as_of_date)` PK, `source`, `created_at`
- `volatility_surface_data` table: `(instrument_id, as_of_date)` PK, `strikes` (json), `expiries` (json), `values` (json row-major)

### Kafka topic

`volatility.surfaces`

### Client in risk-orchestrator

```kotlin
interface VolatilityServiceClient {
    suspend fun getLatestSurface(instrumentId: InstrumentId): VolatilitySurface?
}
```

### MarketDataFetcher case

- `VOLATILITY_SURFACE` → `MatrixMarketData` (new)

---

## Service 4: correlation-service

**Covers:** CORRELATION_MATRIX

### Domain models (in `common`)

```kotlin
data class CorrelationMatrix(
    val labels: List<String>,
    val values: List<Double>,       // row-major, N×N
    val windowDays: Int,
    val asOfDate: Instant,
    val method: EstimationMethod,
)

enum class EstimationMethod { HISTORICAL, EXPONENTIALLY_WEIGHTED, SHRINKAGE }
```

### HTTP endpoints (compute-on-demand approach)

| Endpoint | Returns |
|---|---|
| `POST /api/v1/correlations/compute` | Computed `CorrelationMatrix` |
| `GET /api/v1/correlations/latest?labels=...&window=` | Cached or pre-computed matrix |

### Client in risk-orchestrator

```kotlin
interface CorrelationServiceClient {
    suspend fun getCorrelationMatrix(labels: List<String>, windowDays: Int = 252): CorrelationMatrix?
}
```

### MarketDataFetcher case

- `CORRELATION_MATRIX` → `MatrixMarketData` (new)

---

## New MarketDataValue subtypes

| New subtype | Proto equivalent | Used by |
|---|---|---|
| `CurveMarketData` | `Curve` | YIELD_CURVE, FORWARD_CURVE |
| `MatrixMarketData` | `Matrix` | VOLATILITY_SURFACE, CORRELATION_MATRIX |

`ScalarMarketData` (existing) covers RISK_FREE_RATE, DIVIDEND_YIELD, CREDIT_SPREAD.

---

## Implementation order

1. **rates-service** — unblocks FIXED_INCOME and DERIVATIVE
2. **reference-data-service** — completes FIXED_INCOME and DERIVATIVE (can be built in parallel with rates-service)
3. **volatility-service** — fully unblocks DERIVATIVE
4. **correlation-service** — portfolio-level, lowest urgency (risk engine falls back to identity matrix)
