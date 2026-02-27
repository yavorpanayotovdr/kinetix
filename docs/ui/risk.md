# Risk Tab — VaR, Greeks & Risk Analytics

The Risk tab is the analytical heart of Kinetix, providing Value at Risk (VaR) calculations, Greeks sensitivity analysis, and component breakdowns across three calculation methodologies.

---

## What it displays

### VaR Dashboard (4-column grid layout)

1. **VaR Gauge** (1 column) — Normal distribution curve with shaded regions:
   - Blue fill at the confidence level (95% or 99%) showing VaR
   - Red fill in the tail showing Expected Shortfall (CVaR)
   - Numeric VaR and ES values displayed side-by-side

2. **Risk Sensitivities** (2 columns) — Inline Greeks display:
   - **PV (Portfolio Value)** — displayed when available
   - **Greeks Heatmap** — Delta, Gamma, Vega by asset class with interactive tooltips
   - **Summary metrics** — Theta (time decay) and Rho (rate sensitivity)

3. **Component Breakdown** (1 column) — Donut chart showing VaR contribution by asset class:
   - EQUITY (blue), FIXED_INCOME (green), COMMODITY (amber), FX (purple)
   - Absolute dollar contribution and percentage of total per class
   - Sorted by percentage descending

### VaR Trend

Grafana-style line chart with zoomable time range, pre-populated with VaR history on load. Plots both the **VaR line** (indigo) and the **ES (Expected Shortfall) line** (orange) with area fills. Hover shows both values. Legend toggles each line.

**Metadata bar** shows calculation type (PARAMETRIC / HISTORICAL / MONTE_CARLO) and timestamp. A **Run VaR** button triggers a fresh computation.

### Job History

- Always visible below the VaR Dashboard (non-collapsible)
- Tables and timeline views of past calculation jobs
- Shows status, duration, calculation type, results, and step-by-step execution details
- Pagination bar with total job count

---

## VaR calculation methodologies

| Method | How it works | Best for |
|--------|-------------|----------|
| **Parametric** | Assumes normal returns; uses covariance matrix: `VaR = z × σ_portfolio × √t` | Fast estimates on liquid portfolios |
| **Historical** | 250 correlated scenarios via Cholesky decomposition; empirical percentile | Capturing fat tails |
| **Monte Carlo** | 10,000 simulations with correlated returns; empirical VaR/ES | Complex derivatives, non-linear payoffs |

All three produce:
- **VaR** at the chosen confidence level (95% or 99%)
- **Expected Shortfall** (average loss beyond VaR)
- **Component breakdown** by asset class (via Euler allocation for parametric, empirical for others)

---

## Greeks (finite-difference bumping)

| Greek | Shock Applied | What it measures |
|-------|--------------|------------------|
| **Delta** | ±1% price | VaR sensitivity to price moves |
| **Gamma** | ±1% price | Convexity — Delta's rate of change |
| **Vega** | +1pp vol | VaR sensitivity to volatility |
| **Theta** | -1 day | Time decay effect on VaR |
| **Rho** | +1bp rate | Interest rate sensitivity |

Computed per asset class (EQUITY, FIXED_INCOME, COMMODITY, FX).

---

## Why a trader / investment bank needs this

1. **Quantified downside** — VaR puts a dollar figure on "how much could we lose on a bad day at 95% or 99% confidence," which is the foundation of trading risk limits.
2. **Tail risk awareness** — Expected Shortfall answers "if VaR is breached, how bad does it get on average," capturing extreme scenarios VaR alone misses.
3. **Concentration detection** — The component breakdown immediately reveals if one asset class dominates portfolio risk, enabling rebalancing decisions.
4. **Sensitivity insight** — Greeks tell traders which risk factors matter most. High Vega means a vol spike will hurt; high Rho means rate moves are material.
5. **What-if exploration** — The volatility bump slider lets traders estimate the impact of a vol regime change without running a full stress test.
6. **Methodology flexibility** — Different desks can choose the VaR method that fits their book (parametric for vanilla, Monte Carlo for exotics).
7. **Trend monitoring** — The VaR trend chart reveals whether risk is creeping up over time, allowing proactive intervention.

---

## Architecture

### VaR calculation pipeline (Valuation Job)

```
1. Fetch Positions        → Position Service (gRPC)
2. Discover Dependencies  → Risk Engine (gRPC) — what market data is needed
3. Fetch Market Data      → Price, Rates, Volatility, Correlation services
4. Valuate                → Risk Engine (gRPC, unified Valuate RPC) — VaR + Greeks in one call
5. Publish Result         → Kafka "risk.results" topic
```

The unified `Valuate` RPC accepts `requested_outputs` (VAR, EXPECTED_SHORTFALL, GREEKS) and returns all results in a single `ValuationResponse`, eliminating separate round-trips for VaR and Greeks.

### Data flow

```
UI (VaRDashboard with inline RiskSensitivities, JobHistory)
  → Risk Orchestrator (Kotlin/Ktor, HTTP REST)
    → Risk Engine (Python, gRPC Valuate RPC) — NumPy/SciPy computation
    → Position Service (gRPC) — portfolio holdings
    → Market Data services — vols, correlations, rates, prices
  → Kafka "risk.results" — consumed by Notification Service for alerting
```

Risk Sensitivities (Greeks) render inline within the VaR Dashboard grid when Greeks data is available. Job History renders unconditionally below the dashboard.

### Calculation triggers

- **On-demand** — user clicks Recalculate in the UI
- **Scheduled** — ScheduledVaRCalculator runs periodically
- **Event-driven** — TradeEventConsumer and PriceEventConsumer may trigger recalculation

---

## Key files

| Component | Location |
|-----------|----------|
| VaR Gauge | `ui/src/components/VaRGauge.tsx` |
| VaR Dashboard | `ui/src/components/VaRDashboard.tsx` |
| Component Breakdown | `ui/src/components/ComponentBreakdown.tsx` |
| Risk Sensitivities | `ui/src/components/RiskSensitivities.tsx` |
| Job History | `ui/src/components/JobHistory.tsx` |
| VaR Hook | `ui/src/hooks/useVaR.ts` |
| Greeks Hook | `ui/src/hooks/useGreeks.ts` |
| Risk API | `ui/src/api/risk.ts` |
| Risk Orchestrator Routes | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskRoutes.kt` |
| VaR Calculation Service | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/service/VaRCalculationService.kt` |
| Latest VaR Cache | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/cache/LatestVaRCache.kt` |
| gRPC Risk Client | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/client/GrpcRiskEngineClient.kt` |
| Kafka Publisher | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/kafka/KafkaRiskResultPublisher.kt` |
| Parametric VaR | `risk-engine/src/kinetix_risk/var_parametric.py` |
| Historical VaR | `risk-engine/src/kinetix_risk/var_historical.py` |
| Monte Carlo VaR | `risk-engine/src/kinetix_risk/var_monte_carlo.py` |
| Greeks Calculator | `risk-engine/src/kinetix_risk/greeks.py` |
| Portfolio Risk Dispatcher | `risk-engine/src/kinetix_risk/portfolio_risk.py` |
| Default Volatilities | `risk-engine/src/kinetix_risk/volatility.py` |
| Proto Definitions | `proto/src/main/proto/kinetix/risk/risk_calculation.proto` |

---

## API Endpoints

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/v1/risk/var/{portfolioId}` | POST | Trigger VaR calculation (uses unified Valuate RPC) |
| `/api/v1/risk/var/{portfolioId}` | GET | Fetch cached latest VaR result |
| `/api/v1/risk/greeks/{portfolioId}` | POST | Calculate Greeks (uses unified Valuate RPC) |
| `/api/v1/risk/dependencies/{portfolioId}` | POST | Discover market data dependencies |
| `/api/v1/risk/jobs/{portfolioId}` | GET | List valuation jobs (pagination + date range) |
| `/api/v1/risk/jobs/detail/{jobId}` | GET | Job execution details |

---

## Kafka Topics

| Topic | Direction | Purpose |
|-------|-----------|---------|
| `risk.results` | Published by Risk Orchestrator | VaR results for downstream alerting and persistence |
| `trades.lifecycle` | Consumed by Risk Orchestrator | Trade events that may trigger recalculation |
| `price.updates` | Consumed by Risk Orchestrator | Price changes that may trigger recalculation |
