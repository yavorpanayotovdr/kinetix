# Positions Tab — Real-Time Portfolio View

The Positions tab is the primary view for traders, showing live portfolio holdings with real-time P&L updates streamed via WebSocket.

---

## What it displays

**Portfolio Summary Bar** (3 cards):
- **Position Count** — number of open positions
- **Total Market Value** — sum of all position market values
- **Total Unrealized P&L** — aggregate unrealized profit/loss, color-coded green (profit) or red (loss)

**Position Table** with columns:

| Column | Description |
|--------|-------------|
| Instrument | Ticker symbol (e.g. AAPL, TSLA) |
| Asset Class | EQUITY, FIXED_INCOME, FX, COMMODITY, DERIVATIVE |
| Quantity | Number of units held |
| Avg Cost | Weighted average entry price |
| Market Price | Latest live price from WebSocket stream |
| Market Value | Quantity x Market Price |
| Unrealized P&L | (Market Price - Avg Cost) x Quantity, color-coded |

**Connection Status Badge** — "Live" (green) or "Disconnected" (red) based on WebSocket state.

---

## How positions and P&L work

### Trade application (average cost method)

| Scenario | Average Cost Rule |
|----------|------------------|
| First trade into instrument | Avg Cost = trade price |
| Same-direction trade (buy more) | Weighted average: `(oldQty x oldCost + newQty x newPrice) / totalQty` |
| Opposite-direction trade (partial close) | Avg Cost unchanged |
| Position closes to zero | Avg Cost resets to trade price |

### P&L calculation
- **Market Value** = Quantity x Market Price
- **Unrealized P&L** = (Market Price - Avg Cost) x Quantity

---

## Why a trader / investment bank needs this

1. **Real-time visibility** — Live prices stream over WebSocket so traders see P&L move in real time, not on stale snapshots.
2. **Position awareness** — Knowing exactly what you hold, at what cost, across asset classes prevents unintended concentration or exposure.
3. **Trade impact** — Weighted average cost accounting lets a trader immediately see how a new fill shifts their break-even.
4. **Multi-portfolio support** — Portfolio selector lets the desk switch between books (equity-growth, derivatives-book, macro-hedge, etc.).

---

## Architecture

### Data flow

```
UI (PositionGrid)
  ├── REST: GET /api/v1/portfolios/{id}/positions  →  Gateway  →  Position Service  →  PostgreSQL
  └── WebSocket: /ws/prices  →  Gateway (PriceBroadcaster)  →  live price ticks
```

### Trade booking flow

```
POST /api/v1/portfolios/{id}/trades
  → Gateway
    → Position Service
      1. Idempotency check (duplicate trade returns existing state)
      2. Load or create position
      3. Apply trade (weighted average cost)
      4. Persist trade + updated position (transactional)
      5. Publish TradeEvent to Kafka "trades.lifecycle"
      → 201 Created
```

### Price update flow

```
Kafka "price.updates"
  → Position Service (PriceConsumer)
    → position.markToMarket(newPrice)
    → persist to DB

Kafka / market data source
  → Gateway (PriceBroadcaster)
    → WebSocket push to all subscribed UI sessions
      → usePriceStream hook recalculates marketValue & unrealizedPnl
```

---

## Key files

| Component | Location |
|-----------|----------|
| UI Component | `ui/src/components/PositionGrid.tsx` |
| Positions Hook | `ui/src/hooks/usePositions.ts` |
| Price Stream Hook | `ui/src/hooks/usePriceStream.ts` |
| API Client | `ui/src/api/positions.ts` |
| Position Service Routes | `position-service/src/main/kotlin/com/kinetix/position/routes/PositionRoutes.kt` |
| Trade Booking Service | `position-service/src/main/kotlin/com/kinetix/position/service/BookTradeCommand.kt` |
| Price Update Service | `position-service/src/main/kotlin/com/kinetix/position/service/PriceUpdateService.kt` |
| Position Domain Model | `common/src/main/kotlin/com/kinetix/common/model/Position.kt` |
| Trade Domain Model | `common/src/main/kotlin/com/kinetix/common/model/Trade.kt` |
| Money Value Object | `common/src/main/kotlin/com/kinetix/common/model/Money.kt` |
| WebSocket Broadcaster | `gateway/src/main/kotlin/com/kinetix/gateway/websocket/PriceBroadcaster.kt` |
| Kafka Trade Publisher | `position-service/src/main/kotlin/com/kinetix/position/kafka/KafkaTradeEventPublisher.kt` |
| Kafka Price Consumer | `position-service/src/main/kotlin/com/kinetix/position/kafka/PriceConsumer.kt` |
| Dev Data Seeder | `position-service/src/main/kotlin/com/kinetix/position/seed/DevDataSeeder.kt` |

---

## API Endpoints

### Position Service

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/v1/portfolios` | GET | List all portfolio IDs |
| `/api/v1/portfolios/{portfolioId}/positions` | GET | Fetch positions for a portfolio |
| `/api/v1/portfolios/{portfolioId}/trades` | POST | Book a new trade |

### Gateway (WebSocket)

| Route | Protocol | Purpose |
|-------|----------|---------|
| `/ws/prices` | WebSocket | Subscribe to live price updates per instrument |

---

## Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `trades.lifecycle` | Published by Position Service | Trade execution events for downstream consumers |
| `price.updates` | Consumed by Position Service | Market price updates to mark positions to market |

---

## Database Schema

### positions table
```
portfolio_id        VARCHAR(255)    PK (composite)
instrument_id       VARCHAR(255)    PK (composite)
asset_class         VARCHAR(50)
quantity            NUMERIC(28,12)
avg_cost_amount     NUMERIC(28,12)
market_price_amount NUMERIC(28,12)
currency            VARCHAR(3)
updated_at          TIMESTAMPTZ
```

### trade_events table
```
trade_id       VARCHAR(255)    PK
portfolio_id   VARCHAR(255)    indexed
instrument_id  VARCHAR(255)
asset_class    VARCHAR(50)
side           VARCHAR(10)
quantity       NUMERIC(28,12)
price_amount   NUMERIC(28,12)
price_currency VARCHAR(3)
traded_at      TIMESTAMPTZ     indexed
created_at     TIMESTAMPTZ
```
