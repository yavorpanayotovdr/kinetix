# Phase 2 Codebase Scout Report

## 1. Trade Lifecycle

### Models & Trade Event
- **Location**: `/position-service/src/main/kotlin/com/kinetix/position/kafka/TradeEvent.kt`
- **Trade class**: `com.kinetix.common.model.Trade` (in `common`)
  - Fields: `tradeId`, `portfolioId`, `instrumentId`, `assetClass`, `side`, `quantity`, `price`, `tradedAt`
  - **NO status field** - trades are immutable once created
  - **NO amend/cancel logic** - trades cannot be modified

### Trade Booking Flow
- **Service**: `TradeBookingService` (`position-service/src/main/kotlin/com/kinetix/position/service/BookTradeCommand.kt`)
  - Handles `BookTradeCommand` → creates `Trade` → publishes to Kafka topic "trades"
  - **Idempotency**: checks if trade already exists (by `tradeId`), returns existing if duplicate
  - Updates `Position` using `applyTrade()` logic

### Database Persistence
- **TradeEventsTable** (`position-service/src/main/kotlin/com/kinetix/position/persistence/TradeEventsTable.kt`)
  - Columns: `tradeId` (PK), `portfolioId`, `instrumentId`, `assetClass`, `side`, `quantity`, `priceAmount`, `priceCurrency`, `tradedAt`, `createdAt`
  - **NO status/amendment tracking**

- **PositionsTable** (`position-service/src/main/kotlin/com/kinetix/position/persistence/PositionsTable.kt`)
  - Columns: `portfolioId`, `instrumentId` (composite PK), `assetClass`, `quantity`, `avgCostAmount`, `marketPriceAmount`, `currency`, `updatedAt`
  - Stores aggregate position state only

### Routes (Position Service)
- `POST /api/v1/portfolios/{portfolioId}/trades` - Books a trade
- `GET /api/v1/portfolios/{portfolioId}/positions` - Gets current positions
- `GET /api/v1/portfolios` - Lists all portfolios

---

## 2. Trade Blotter

### Trade History Repository
- **Interface**: `TradeEventRepository` (`position-service/src/main/kotlin/com/kinetix/position/persistence/TradeEventRepository.kt`)
  - Methods: `findByTradeId(tradeId)`, `findByPortfolioId(portfolioId)` ✓ **exists**
  - Returns trades ordered by `tradedAt` ascending

### UI Components
- **Position display**:
  - `ui/src/components/PositionGrid.tsx` - Shows current positions
  - `ui/src/components/PositionRiskTable.tsx` - Risk metrics
  - `ui/src/api/positions.ts` - API calls to fetch positions

- **NO dedicated trade blotter/history UI** - only positions tab exists
  - Tabs: Positions, P&L, Risk, Scenarios, Regulatory, Alerts, System
  - **Need to add trade history tab and UI**

### Gateway Routing
- `gateway/src/main/kotlin/com/kinetix/gateway/routes/PositionRoutes.kt`
  - Proxies to position-service, no additional filtering/enrichment

---

## 3. Pre-Trade Limits

### Current Limit Checking
- **NO pre-trade limit checking** found in gateway or position-service
- Route accepts any `BookTradeRequest` without validation
- Only basic bounds checks: quantity > 0, price >= 0

### Notification/Alerts System
- **AlertRuleRepository** (`notification-service/src/main/kotlin/com/kinetix/notification/persistence/AlertRuleRepository.kt`)
  - Stores `AlertRule` with threshold, operator (GREATER_THAN, LESS_THAN, EQUALS), severity
  - **Alert types**: VAR_BREACH, PNL_THRESHOLD, RISK_LIMIT
  - **NO pre-trade specific alert type** - only post-trade monitoring

- **RiskResultConsumer** consumes VaR/risk results and triggers alerts
- **AnomalyEventConsumer** watches for anomalies

### Gateway Structure
- `gateway/src/main/kotlin/com/kinetix/gateway/` - HTTP routing layer
- Routes proxy to downstream services; no central limit checking logic

---

## 4. Realized P&L

### Current P&L Calculation
- **Position.unrealizedPnl**: `(marketPrice - averageCost) * quantity`
  - Located in `common/src/main/kotlin/com/kinetix/common/model/Position.kt`
  - **Only unrealized P&L** - no realized P&L tracking

- **Portfolio.totalUnrealizedPnl(currency)**: sums unrealized across positions in given currency

### Missing Realized P&L
- **NO realized P&L model** - trades are not linked to historical P&L
- **NO P&L attribution** to individual trades or closing events
- **Challenge**: Position model only stores current aggregate state; history is lost

---

## 5. Multi-Currency Support

### Position Currency
- **Position.currency**: derived from `marketPrice.currency`
- **Money** class: `amount: BigDecimal, currency: Currency` (from Java `Currency`)
- **Stored in DB**: `currency` varchar(3) column on PositionsTable

### Rates Service
- **Location**: `rates-service/src/main/kotlin/com/kinetix/rates/`
- **Available FX data**:
  - `RiskFreeRate` - interest rates by currency
  - `YieldCurve` - term structure
  - `ForwardCurve` - forward rates

- **No explicit FX conversion logic** found in position-service
- Risk-orchestrator likely handles currency aggregation (receives positions from position-service)

### Gateway Service Clients
- `HttpRiskServiceClient` - calls risk-orchestrator
- Risk-orchestrator handles multi-currency aggregation for risk calculations

---

## 6. Build Configuration

### Position Service
- **File**: `position-service/build.gradle.kts`
- Dependencies:
  - `:common` module
  - Exposed (ORM), database drivers
  - Kafka clients
  - kotlinx.serialization
  - Testcontainers (PostgreSQL, Kafka)

### Gateway Service
- **File**: `gateway/build.gradle.kts`
- Dependencies:
  - `:common` module
  - Ktor (server + client)
  - JWT auth, WebSockets
  - Client mocking for tests
  - No database (pure HTTP proxy)

### Common Module
- Houses shared models: `Trade`, `Position`, `Money`, value objects (`TradeId`, `PortfolioId`, etc.)

---

## Key Architectural Insights

### What Exists
1. ✓ Basic trade booking with idempotency
2. ✓ Position aggregation with unrealized P&L
3. ✓ Multi-currency awareness
4. ✓ Alert/threshold framework (but post-trade only)
5. ✓ Rates service with FX/interest data
6. ✓ Repository pattern for clean separation

### What's Missing for Phase 2
1. **Trade amendments/cancellations** - No status field; trades immutable
2. **Trade blotter UI** - No history tab; only position snapshots
3. **Realized P&L** - No closure tracking; history lost at position aggregation
4. **Pre-trade limits** - No validation layer before booking
5. **Trade-level P&L attribution** - No realized/mark-to-market per trade
6. **Trade status tracking** - No lifecycle states (PENDING, FILLED, AMENDED, CANCELLED)

### Database Schema Gaps
- `TradeEventsTable` needs: `status`, `originalTradeId` (for amendments), `reason` (for cancellations)
- Need new table: `TradeClosures` or extend positions to track realized gains/losses
- Need audit trail table for amendments/cancellations

### API Gaps
- `GET /api/v1/portfolios/{portfolioId}/trades` - list/filter trade history (partially implemented via repo)
- `PUT /api/v1/portfolios/{portfolioId}/trades/{tradeId}` - amend trade (doesn't exist)
- `DELETE /api/v1/portfolios/{portfolioId}/trades/{tradeId}` - cancel trade (doesn't exist)
- `GET /api/v1/portfolios/{portfolioId}/trades/{tradeId}/realized-pnl` - realized P&L (doesn't exist)
