# Kinetix Phase 2 — Implementation Plan (Directions 1-9)

Consolidated plan produced by a cross-functional review team: Trader, Architect, QA, UX Designer, and Data Analyst. Each direction was independently analysed from all five perspectives, then synthesised into a unified implementation plan.

**Scope:** Directions 1 through 9 from the [Trader Review](trader-review-23.03.2026.md). Direction 10 (Algo/Systematic) is out of scope.

---

## Build Order and Dependencies

The dependency graph dictates the build sequence. Three phases allow maximum parallelism while respecting hard dependencies.

### Phase A — No Dependencies (build in parallel)

| Direction | Priority | Effort | Key Deliverable |
|-----------|----------|--------|-----------------|
| 1. Real-Time Intraday P&L | HIGH | Medium | Streaming P&L ticker, intraday chart |
| 3. Liquidity Risk Layer | HIGH | Medium-High | LVaR, concentration flags, ADV data |
| 6. Historical Scenario Replay | MEDIUM | Medium | Date-range replay, reverse stress, scenario library |

### Phase B — Depends on Phase A

| Direction | Depends On | Priority | Effort |
|-----------|-----------|----------|--------|
| 4. Factor Decomposition | Dir 1 (P&L series) | MEDIUM-HIGH | Medium |
| 8. Multi-Desk Aggregation | Cross-book VaR (exists) | MEDIUM-LOW | Low-Medium |
| 9. Market Regime Detection | Dir 1 + Dir 3 (signals) | LOWER | Medium |

### Phase C — Heavier Dependencies

| Direction | Depends On | Priority | Effort |
|-----------|-----------|----------|--------|
| 2. Hedge Recommendations | Dir 3 (liquidity) + Dir 4 (factors) | HIGH | High |
| 7. Counterparty Credit Risk | Independent, but Dir 1 helps CVA | MEDIUM | High |
| 5. Order/Execution Integration | Independent, needs new service approval | MEDIUM | Very High |

```
Direction 1 (Intraday P&L)
  +-- feeds Dir 4 (factor P&L attribution needs intraday time series)
  +-- feeds Dir 9 (regime detection uses P&L vol as signal)
  +-- feeds Dir 8 (desk-level intraday aggregation)

Direction 3 (Liquidity)
  +-- prerequisite for Dir 2 (hedge engine respects liquidity)
  +-- feeds Dir 6 (stressed liquidity as stress parameter)
  +-- feeds Dir 7 (collateral liquidation haircuts)

Direction 4 (Factor Model)
  +-- improves Dir 2 (factor-aware hedging)
  +-- feeds Dir 8 (desk-level factor decomposition)

Direction 8 (Hierarchy)
  +-- pure consumer — progressively enriched as others ship
```

---

## Direction 1: Real-Time Intraday P&L

### Architecture

**Services affected:** risk-orchestrator, gateway, notification-service, UI. No new services.

**Data flow:** `price.updates` → risk-orchestrator `IntradayPnlService.recompute(bookId)` → write `intraday_pnl_snapshots` → publish `IntradayPnlEvent` to `risk.pnl.intraday` → gateway `PnlBroadcaster` → WebSocket `/ws/pnl` → UI `useIntradayPnlStream` hook → P&L ticker strip.

**Data model:**
- New hypertable `intraday_pnl_snapshots` (risk-orchestrator V35): book_id, snapshot_at, total_pnl, realised_pnl, unrealised_pnl, delta/gamma/vega/theta/rho_pnl, high_water_mark, currency. 30-day retention, 1-hour chunks, compress after 1 day.
- New continuous aggregate `minute_pnl_summary`: 1-minute bucketed snapshots, refreshed every 30s.
- New Kafka topic `risk.pnl.intraday` (3 partitions, key=bookId).
- New Kafka event `IntradayPnlEvent` in common module.
- Redis cache `pnl:latest:{bookId}` with 5s TTL for WebSocket reads.

### UI Design

**Tier 1 — Permanent ticker strip** between the tab bar and main content (24px height). Four numbers: SOD, Realised, Unrealised, Total. Persists on every tab. Green/red colour coding via existing `pnlColorClass`. Brief background flash (100ms) on update — green if improved, red if deteriorated. `aria-live="polite"` with 2s throttle.

**Tier 2 — Enhanced PnlTab** with "Intraday" sub-tab. KPI strip (4 cards), intraday P&L chart (SVG line chart with trade markers), asset class decomposition table with sparklines.

**Tier 3 — Position grid** gets an intraday P&L change column.

### Implementation Steps (TDD)

1. Flyway V35: `intraday_pnl_snapshots` hypertable. Migration test.
2. `IntradayPnlEvent` in common. Schema compatibility test.
3. Kafka topic `risk.pnl.intraday` in `create-topics.sh`.
4. `IntradayPnlRepository` interface + Exposed impl. Integration test (Testcontainers).
5. `IntradayPnlService` — reuses `PnlAttributionService`, tracks high-water mark in `ConcurrentHashMap<String, BigDecimal>`. Unit tests with mocked repos.
6. `KafkaIntradayPnlPublisher`. Integration test.
7. Wire into `PriceEventConsumer` after VaR recalc. Update existing integration tests.
8. `PnlBroadcaster` in gateway (same pattern as `PriceBroadcaster`). Unit test.
9. `PnlWebSocketRoute` (`/ws/pnl`). Acceptance test via `testApplication`.
10. `KafkaIntradayPnlConsumer` in gateway. Integration test.
11. Intraday P&L REST route in risk-orchestrator + gateway proxy. Acceptance tests.
12. UI: `useIntradayPnlStream` hook. Vitest tests.
13. UI: `PnlTickerStrip` component. Vitest + Playwright tests.
14. UI: `IntradayPnlChart` with trade markers. Playwright E2E.
15. UI: Realised/unrealised decomposition chart. Playwright E2E.

### Critical Test Cases

- P&L updates within 500ms of price tick (end-to-end latency test).
- High-water mark is accurate: P&L hits +$2M at 10:15, falls to +$1M by 11:00 — mark shows $2M.
- Concurrent trade + price race condition: trade booking and price tick arrive within the same 100ms window — P&L is correct (no double-count, no missed update).
- No SOD baseline: service logs warning and skips; UI shows "Awaiting SOD baseline."
- Multi-currency FX conversion uses live rates, not static.
- 200 instruments ticking at 1Hz: debounce P&L writes to 1s per book, still push WebSocket.

### Risks

- **Multi-currency aggregation lag.** Current `StaticFxRateProvider` will produce wrong aggregated P&L when FX moves. Needs `LiveFxRateProvider` feeding from price-service.
- **Event storm.** 200 instruments × 1Hz = 200 Kafka messages/second. Debounce P&L writes to max 1/second per book. Still compute in-memory for WebSocket push.
- **SOD persistence.** If system restarts mid-day, SOD baseline must survive in DB, not just in-memory.

---

## Direction 2: Hedge Recommendation Engine

### Architecture

**Services affected:** risk-orchestrator (new `HedgeRecommendationService`), risk-engine (new `hedge_optimizer.py`), gateway, UI. No new services for Phase 1.

**Phase 1 (analytical hedges):** Delta/vega/gamma neutralisation computed in Kotlin within risk-orchestrator. Uses per-instrument Greeks from the existing cache.

**Phase 2 (VaR-minimising):** New gRPC method `SuggestHedge` on `RiskCalculationService`. Python `scipy.optimize.minimize` with SLSQP.

**Data model:**
- New table `hedge_recommendations` (risk-orchestrator V36): book_id, target_metric, recommendations JSONB, constraints JSONB, base/post-hedge Greeks.
- Proto extension: `HedgeSuggestionRequest/Response` messages.
- New reference-data field: `hedging_eligible: Boolean` on instruments.

**API:** `POST /api/v1/risk/hedge-suggest/{bookId}` with `{targetMetric, targetReductionPct, maxSuggestions, constraints}`.

### UI Design

**Entry:** "Suggest Hedge" button on Risk tab (next to What-If). Keyboard: Shift+H.

**Right-side drawer** (480px, same pattern as WhatIfPanel). Target selection radio group (VaR/Delta/Vega/Gamma) showing current values. Constraints section. Progress indicator during computation.

**Recommendation cards** (max 5, ranked by cost-effectiveness): instrument, side, quantity, cost, Greek reduction, residual exposure mini-gauge. "Send to What-If" button per card. "Apply All" button for combined hedge.

### Implementation Steps

1. `HedgeSuggestion`/`HedgeRecommendationRequest`/`Response` domain models. Unit tests.
2. Flyway V36: `hedge_recommendations`. `HedgeRecommendationRepository`. Integration test.
3. `AnalyticalHedgeCalculator` — pure function. Unit tests: delta neutralisation, vega neutralisation, constraint application, Lyapunov check (reducing one Greek doesn't worsen others beyond threshold).
4. `HedgeRecommendationService`. Unit tests with mocks.
5. Routes in risk-orchestrator. Acceptance tests.
6. Gateway proxy. Acceptance tests.
7. Proto `SuggestHedge` gRPC method. Python `HedgeOptimizerServicer`. Unit tests.
8. Wire into `server.py`. gRPC contract integration test.
9. `GrpcHedgeSuggestionClient` in risk-orchestrator. Unit test with MockK.
10. UI: `HedgeRecommendationPanel`. Vitest + Playwright tests.

### Critical Test Cases

- Engine suggests hedge within 5 seconds for a 50-candidate universe.
- Suggested hedge reduces stated Greek by ~requested percentage (within 10%) when validated in what-if.
- System refuses to suggest a hedge that would breach position limits at any hierarchy level.
- Full Greek impact shown per suggestion (not just target Greek).
- Stale instrument prices flagged with `instrumentDataQuality: STALE`.

### Risks

- **Direction 3 is prerequisite.** Without liquidity scoring, optimiser suggests illiquid instruments where bid-offer widens 10x under stress.
- **Model dependency.** Hedge quality depends on Greek accuracy. Cross-Greeks (vanna, volga) add complexity.
- **False confidence.** Analytical approximations under linearised model. Mandatory "validate in what-if" before execution.

---

## Direction 3: Liquidity Risk Layer

### Architecture

**Services affected:** reference-data-service (liquidity metadata), risk-engine (LVaR calculation), risk-orchestrator (orchestration), position-service (concentration alerts), notification-service (new alert type), UI. No new services.

**Data model:**
- New table `instrument_liquidity` (reference-data-service): instrument_id, as_of_date, avg_daily_volume, bid_ask_spread_bps, market_depth_score, liquidity_tier, source. TimescaleDB hypertable, 5-year retention.
- New table `position_liquidity_metrics` (risk-orchestrator): per-position ADV%, liquidation days, LVaR, concentration flag. Computed daily + on trade events.
- New Python module `liquidity.py`: `compute_liquidation_horizon()`, `compute_lvar()`, `compute_stressed_liquidation_value()`, `assess_concentration_flag()`.
- Proto: `LiquidityAdjustedVaRRequest/Response`, `LiquidityInput`, `PositionLiquidityRisk`.
- New gRPC method: `CalculateLiquidityAdjustedVaR` on `RiskCalculationService`.
- New Kafka topic: `liquidity.daily-metrics` (3 partitions).
- New alert type: `LIQUIDITY_CONCENTRATION`.

**External data needed:** ADV (from market data provider, daily batch). Bid-ask spreads (Level 1 data, daily average). Volume field must be added to price-service schema.

### UI Design

**Inline in PositionGrid:** Liquidity Score as a compact bar (48x6px, green/amber/red). Concentration Flag as a Badge. Both togglable via existing column visibility mechanism.

**Risk tab additions:** LVaR displayed below ES in VaRGauge. New collapsible "Liquidity Risk" section with Liquidation Horizon card, Concentration Flags table, Stressed Liquidity summary.

**New alert rules:** CONCENTRATION_BREACH, LIQUIDITY_SCORE_BELOW, LIQUIDATION_HORIZON_ABOVE.

### Implementation Steps

1. Python `liquidity.py` with all functions. Unit tests.
2. Proto extension. buf lint.
3. Python `LiquidityAdjustedVaRServicer`. Unit tests.
4. `LiquidityRiskResult` domain model + `LiquidityRiskEvent` in common.
5. reference-data-service Flyway migration + repository + routes. Acceptance tests.
6. `HttpReferenceDataServiceClient.getLiquidityData()`. Unit test with MockEngine.
7. `GrpcLiquidityClient` in risk-orchestrator. Unit test.
8. Flyway V37: `liquidity_risk_snapshots`. Repository. Integration test.
9. `LiquidityRiskService` in risk-orchestrator. Unit tests.
10. Routes in risk-orchestrator + gateway proxy. Acceptance tests.
11. Dev data seeder: ADV/spread data for all 11 instrument types.
12. UI: `LiquidityRiskPanel`, `LiquidityScoreBar`. Vitest + Playwright.
13. Notification-service: `LiquidityConcentrationRule`. Unit test.

### Critical Test Cases

- Pre-trade warning when position would exceed 5% of ADV.
- GFC stressed LVaR is 30-100% higher than standard VaR for illiquid books.
- ADV = 0 guard: no division-by-zero; instrument marked ILLIQUID by default.
- Instrument with no ADV data defaults to conservative 10-day horizon.
- Liquidation horizon for options uses delta-adjusted underlying ADV.

### Risks

- **ADV staleness during crisis.** ADV computed from last 20-90 days. Bid-ask spread is a better crisis indicator.
- **Volume data gap.** Price-service schema change is Day 1 prerequisite.
- **Options liquidity.** Strike-expiry combinations each have their own liquidity profile. Use delta-adjusted underlying ADV as proxy.

---

## Direction 4: Factor-Based Risk Decomposition

### Architecture

**Services affected:** risk-engine (new `factor_model.py`), risk-orchestrator, gateway, UI. No new services.

**Factors:** Equity beta (SPX), Rates duration (10Y UST), Credit spread DV01 (CDX IG), FX delta, Vol exposure (VIX-proxy).

**Data model:**
- New Python module `factor_model.py`: per-position factor loading estimation (OLS for equities, analytical for bonds/swaps/options), factor VaR decomposition, factor P&L attribution.
- Proto: `FactorDecompositionRequest/Response`, `FactorContribution`.
- New gRPC method: `DecomposeFactorRisk` on `RiskCalculationService`.
- New table `factor_decomposition_snapshots` (risk-orchestrator V38).
- New tables: `factor_returns` (daily factor return series), `instrument_factor_loadings`.
- Benchmark index `IDX-SPX` seeded in reference-data-service with historical prices.

**External data needed:** VIX and CDX IG daily series. 2-year minimum for loading estimation, 10 years for stress testing.

### UI Design

**New "Factor" sub-tab on the Risk tab.** Two-column layout:
- Left (60%): Factor Attribution Chart (bidirectional horizontal bars by factor, reusing PnlWaterfallChart SVG pattern) + Factor VaR Decomposition table.
- Right (40%): Factor Exposures card (5 numbers: beta, DV01, FX delta, credit DV01, vega) + Macro Correlation mini-heatmap.

**Factor Concentration Warning banner** when any single factor > 60% of VaR.

### Implementation Steps

1. `factor_model.py` — start with equity beta decomposition. Unit tests with synthetic returns.
2. Extend to DV01, FX delta, credit spread, vega. Unit tests per factor type.
3. Proto extension. buf lint.
4. Python `FactorDecompositionServicer`. Unit tests.
5. Seed `IDX-SPX` instrument + synthetic historical prices.
6. Extend `DependenciesDiscoverer` for benchmark historical prices. Unit test.
7. `FactorDecompositionResult` domain + Flyway V38 + repository. Integration test.
8. `FactorRiskService` in risk-orchestrator. Unit tests.
9. Routes + gateway proxy. Acceptance tests.
10. Extend `ScheduledVaRCalculator` to run factor decomposition post-valuation. Unit test.
11. UI: `FactorDecompositionChart` + `FactorExposuresCard`. Playwright E2E.
12. UI: Factor P&L attribution history chart. Playwright E2E.

### Critical Test Cases

- Pure equity book: >95% of VaR explained by equity beta.
- Pure rates book: equity beta exposure near zero.
- Factor P&L reconciles with total P&L to within 10%.
- Beta instability: use EWMA beta (lambda=0.94) to weight recent observations.
- Cross-asset positions (convertible bonds): no double-counting.

---

## Direction 5: Order/Execution Management Integration

### Architecture

**New service required:** `fix-adapter` (Kotlin + QuickFIX/J). Maintains FIX sessions, converts ExecutionReports to `TradeEventMessage`, publishes to `trades.lifecycle`. Approval required before creation.

**Other services affected:** position-service (execution records, reconciliation), gateway (FIX status in SystemDashboard), UI.

**Data model:**
- New table `execution_records` (position-service V15): trade_id, fix_exec_id, arrival_price, execution_price, slippage_bps, venue, algorithm.
- New table `execution_orders`: order lifecycle tracking.
- New Kafka topics: `fix.execution.reports` (raw FIX), `orders.arrivals`.
- Extend `TradeEventMessage`: `fixExecId`, `venue`, `arrivalPrice` (nullable for backward compat).
- New table `prime_broker_reconciliation`.

**Pre-trade risk gateway:** Standalone `POST /api/v1/risk/pre-trade-check` endpoint exposing `HierarchyBasedPreTradeCheckService` without persisting. Target: <100ms including DB. Cache limits in Redis with 30s TTL.

### UI Design

**FIX adapter status:** New check in DataQualityIndicator dropdown.

**Trades tab sub-tabs:** Blotter | Pre-Trade | Recon.

**Pre-trade queue:** Compact table above blotter with mini pass/fail indicators (three dots for position/notional/VaR limits). Red left border on rejects.

**Reconciliation view:** Two-panel diff (internal vs prime broker positions). Break rows highlighted in amber.

**Execution cost analysis:** Per-instrument slippage table + arrival vs execution price chart.

### Implementation Steps

1. `fix-adapter` service skeleton (requires approval). `FIXSessionManager`. Unit tests for message parsing.
2. `FIXExecutionReportConverter`. Unit tests with sample FIX messages.
3. `KafkaFIXEventPublisher`. Integration test.
4. New Kafka topics in `create-topics.sh`.
5. `TradeEventMessage` extension. Schema compatibility test.
6. position-service V15. `ExecutionRecordRepository`. Integration test.
7. `ExecutionCostService`. Unit tests.
8. Execution cost routes + gateway proxy. Acceptance tests.
9. Reconciliation endpoint + prime broker snapshot import. Acceptance test.
10. Gateway: FIX session status in SystemDashboard. Playwright E2E.
11. UI: Execution cost view in Trades tab. Playwright E2E.

### Critical Test Cases

- FIX fill → position update within 1 second.
- Pre-trade check rejects order breaching firm-level VaR limit.
- Duplicate FIX fills (network recovery): idempotent on ExecID. Test with Kafka stop/restart.
- Partial fills aggregated correctly.
- Pre-trade check <50ms at p99 under 100 concurrent requests (Gatling).
- FIX-originated trades still go through `HierarchyBasedPreTradeCheckService`.

### Risks

- **FIX complexity.** Session-level heartbeats, sequence numbers, gap fill. QuickFIX/J handles most of this but requires persistent FileStore. MVP: REST-based fill submission endpoint.
- **Pre-trade latency.** 4 DB round-trips for hierarchy checks. Cache in Redis.
- **New service.** Must get approval per ADR constraints.

---

## Direction 6: Historical Scenario Replay & Custom Stress Builder

### Architecture

**Services affected:** risk-engine (new `historical_replay.py`, `reverse_stress.py`), risk-orchestrator, regulatory-service (scenario library), gateway, UI. No new services.

**What already exists:** `StressScenario` model, `run_stress_test()` engine, 4 hardcoded scenarios, `CustomScenarioBuilder.tsx`, `ScenarioGovernancePanel.tsx`, `var_historical.py` already handles `historical_returns` parameter.

**Data model:**
- New Python modules: `historical_replay.py`, `reverse_stress.py` (bisection/gradient descent solver).
- Proto: `HistoricalReplayRequest/Response`, `ReverseStressRequest/Response`, `DailyReplayPnl`.
- Extend `stress_scenarios` table: `scenario_type` (HISTORICAL_REPLAY, CUSTOM, REVERSE), `historical_period_start/end`, `version`, `parent_scenario_id`.
- New table `historical_scenario_periods` + `historical_scenario_returns` (pre-computed return matrices for named crisis periods).
- New Kafka topic: `risk.scenario.published`.

### UI Design

**Historical Replay:** Date-range picker (reuse EodDateRangePicker) + preview sparklines + "Apply to Current Portfolio" button. Results as new entry in ScenarioComparisonTable with Badge variant="info".

**Reverse Stress:** "Reverse Stress" button in ScenarioControlBar → modal dialog with target loss input + factor constraint sliders. Results in comparison table with Badge variant="preclose" (purple).

**Scenario Library:** Replace governance panel list with card grid (3 columns). Status badges (APPROVED/PENDING/DRAFT). Favourite/bookmark with localStorage persistence.

### Implementation Steps

1. `historical_replay.py`. Unit tests with synthetic 5-day returns.
2. `reverse_stress.py` (bisection solver). Unit tests with known analytical cases.
3. Proto extensions. buf lint.
4. Python `StressTestServicer` extended. Unit tests.
5. `HistoricalReplayClient` + `ReverseStressClient` in risk-orchestrator. Unit tests.
6. Extend `MarketDataFetcher` for date-range historical prices. Unit test.
7. Routes in risk-orchestrator. Acceptance tests.
8. Gateway proxy. Acceptance tests.
9. regulatory-service: extend `StressScenario` model + repo. Flyway migration. Acceptance tests.
10. Seed historical return data for GFC, COVID, Taper Tantrum, Euro Crisis.
11. UI: Historical replay panel + chart. Playwright E2E.
12. UI: Reverse stress dialog + result display. Playwright E2E.
13. UI: Scenario library grid with search/sort/favourite. Playwright E2E.

### Critical Test Cases

- March 2020 replay produces correct P&L (validated against known market returns).
- Reverse stress converges within 100 iterations. Non-convergence fails gracefully.
- Date range spanning market holiday: skip or interpolate (documented choice).
- Instrument not in historical data: zero contribution + warning, not error.
- Custom scenario with all shocks = 0: VaR equals base VaR.
- Non-positive-definite correlation matrix in custom scenario: auto-corrected + warning.

---

## Direction 7: Counterparty & Credit Risk Dashboard

### Architecture

**Services affected:** risk-engine (new `credit_exposure.py`), reference-data-service (counterparty master, netting agreements), position-service (collateral, netting-aware exposure), risk-orchestrator (PFE/CVA orchestration), gateway, UI. No new services.

**Data model:**
- New Python module `credit_exposure.py`: `calculate_pfe()` (Monte Carlo exposure paths), `calculate_cva()` (PD × LGD × EPE integral).
- Proto: new `counterparty_risk.proto` with `CounterpartyRiskService`, `CalculatePFE`, `CalculateCVA`.
- New tables in reference-data-service: `counterparty_master` (legal_name, ratings, PD, LGD, sector), `netting_agreements`.
- New tables in position-service: `collateral_balances`, `netting_set_trades`.
- New hypertable in risk-orchestrator: `counterparty_exposure_history` (3-year retention).

**External data needed:** Counterparty credit ratings, CDS spreads per counterparty.

### UI Design

**New "Counterparty" sub-tab on Risk tab** (alongside Dashboard, Run Compare, Factor).

Summary strip: Total Gross Exposure, Total Net Exposure, CVA Total.

**Counterparty table** (left 55%): sorted by gross exposure descending. Columns: Counterparty, Rating (colour-coded by credit quality), Gross/Net Exposure, CVA, Credit Limit, Utilisation bar, Wrong-Way Risk badge.

**Detail panel** (right 45%): PFE chart (expected + 95th percentile confidence band), Netting Set summary, Collateral summary.

### Implementation Steps

1. `credit_exposure.py`: `calculate_pfe()` and `calculate_cva()`. Unit tests.
2. `counterparty_risk.proto`. Python servicer. Unit tests.
3. Wire into `server.py`. gRPC contract integration test.
4. reference-data-service: counterparty master + netting agreement tables + routes. Acceptance tests.
5. Seed counterparties for existing dev data.
6. position-service: `collateral_balances`. `CollateralTrackingService`. Acceptance tests.
7. Extend `CounterpartyExposureService` for netting-set-aware net exposure. Unit tests.
8. risk-orchestrator: `CounterpartyRiskOrchestrationService`. Routes. Acceptance tests.
9. Gateway routes. Acceptance tests.
10. UI: `CounterpartyRiskDashboard`. Playwright E2E.

### Critical Test Cases

- PFE at 95% > current MtM for all risky counterparties.
- PFE profile peaks at maturity hump for swap books.
- CVA > 0 for all sub-AAA rated counterparties.
- CVA = 0 for riskless (CDS spread = 0) counterparties.
- Wrong-way risk flag: bank counterparty + financial sector exposure → flagged.
- Negative MtM (you owe counterparty): net exposure = 0.
- CDS data unavailable: fall back to sector-average spread + prominent warning.
- PFE simulation for 100 counterparties × 10 trades each < 30 seconds.

### Risks

- **PFE compute cost.** 10K Monte Carlo paths per counterparty with 50+ counterparties is expensive. Must be nightly batch, not on-demand. UI shows "as of" timestamp.
- **Collateral data quality.** Simplified model (daily manual update) for Phase 1.

---

## Direction 8: Multi-Desk Risk Aggregation & Reporting

### Architecture

**Services affected:** risk-orchestrator (hierarchy aggregation), regulatory-service (PDF report), position-service (VaR budget limit type), gateway, UI. No new services.

**What already exists:** `CrossBookVaRCalculationService` with Euler allocation, `BookVaRContribution` with marginal/incremental VaR, `HierarchySelector` + `HierarchyBreadcrumb`, `LimitHierarchyService`.

**Data model:**
- Extend `LimitType` enum with `VAR_BUDGET`.
- New hypertable `risk_hierarchy_snapshots` (risk-orchestrator V39): level, entity_id, var_value, pnl_today, limit_utilisation, marginal_var, top_contributors JSONB.
- New table `risk_budget_allocations`: entity_level, entity_id, budget_type, budget_amount, effective dates.
- New materialised view `desk_risk_summary` (joins desks → book_hierarchy → valuation_jobs → limits).
- Missing link: `book_hierarchy` table mapping book_id → desk_id.

### UI Design

**Extend existing Risk tab Dashboard** when `aggregatedView` is true:
- Persistent breadcrumb (HierarchyBreadcrumb) at top of Risk tab content.
- Generalised `HierarchyContributionTable` (replaces BookContributionTable at desk/division level).
- Risk Budget Utilisation card with compact bars (green/amber/red).
- Marginal VaR column surfaced in contribution table.
- Scope indicator stripe below breadcrumb: indigo for firm, blue for division.

**CRO Report:** "Generate Report" button (visible in aggregated view). Modal for date range + section selection + format (PDF/CSV). Async generation with download.

### Implementation Steps

1. Extend `LimitType` with `VAR_BUDGET`. Update `HierarchyBasedPreTradeCheckService`. Unit tests.
2. Establish `book_hierarchy` table mapping. Migration.
3. Flyway V39: `risk_hierarchy_snapshots`. Repository. Integration test.
4. `HierarchyRiskService` in risk-orchestrator. Unit tests.
5. Routes + gateway proxy. Acceptance tests.
6. `ScheduledVaRCalculator` extension: hierarchy aggregation post per-book VaR. Unit test.
7. `desk_risk_summary` materialised view. Refresh on EOD promotion.
8. Python: PDF format in `RegulatoryReportingServicer` (requires `weasyprint` approval). Unit tests.
9. Gateway: PDF report route. Acceptance test.
10. UI: `HierarchyContributionTable` + drill-down navigation. Playwright E2E.
11. UI: Risk Budget Utilisation panel. Playwright E2E.
12. UI: CRO Report download button. Playwright E2E.

### Critical Test Cases

- Firm VaR <= sum of standalone desk VaRs (diversification benefit).
- Incremental VaR of all desks sums to total firm VaR (Euler property).
- Drill-down from firm to position < 5 clicks, < 3 seconds per level.
- One book fails during aggregation: partial result with warning, not null.
- Empty desk: contributes zero VaR without division-by-zero.
- CRO report generates in < 60 seconds without manual intervention.

---

## Direction 9: Market Regime Detection

### Architecture

**Services affected:** risk-engine (new `regime_detector.py`), risk-orchestrator (adaptive parameters, scheduled detection), notification-service (regime alerts), gateway, UI. No new services.

**What already exists:** `AnomalyDetector` (IsolationForest), `VolatilityLSTM`, `MLPredictionService` gRPC — all disconnected from risk workflow.

**Data model:**
- New Python module `regime_detector.py`: rule-based classifier + IsolationForest integration.
  - NORMAL: 20d vol < 1.5× 1yr median, correlation < 0.7.
  - ELEVATED_VOL: 20d vol > 1.5× median.
  - CRISIS: 20d vol > 2.5× median OR cross-asset correlation > 0.75.
- Proto: `RegimeDetectionRequest/Response` on `MLPredictionService`.
- New hypertable `market_regime_history` (risk-orchestrator V40).
- New Kafka event `MarketRegimeEvent` + topic `risk.regime.changes`.
- New table `regime_model_config` (regulatory-service): thresholds, approved parameters.
- Redis: `regime:current` caches active regime for VaR parameter lookup.

**Adaptive parameters:**
- NORMAL → PARAMETRIC, CL_95, standard correlations.
- ELEVATED_VOL → HISTORICAL, CL_99, EWMA volatility.
- CRISIS → MONTE_CARLO, CL_99, 5-day horizon, stressed correlations.

### UI Design

**Header indicator:** 16px circle (green/amber/red) next to DataQualityIndicator. Slow pulse animation in Elevated/Crisis. Click → dropdown showing signals, thresholds, 7-day sparkline, "View full analysis" link.

**New "Regime" sub-tab on Risk tab:** Current Regime card (large label + timeline showing 90-day history as colour-coded strip). Signal panel (table with sparklines per signal). Adaptive parameters comparison card (current vs normal).

**Early warning:** Leading indicators with progress bars toward thresholds. Regime change alerts through existing NotificationCenter.

**Crisis banner:** When in Crisis regime, amber banner on Risk tab: "Crisis regime detected. Risk parameters adapted: stressed correlations, Monte Carlo VaR."

### Implementation Steps

1. `regime_detector.py`: rule-based classifier. Unit tests with synthetic vol/correlation data.
2. Proto extension. buf lint.
3. Python `MLPredictionServicer.DetectRegime()`. Unit tests.
4. `MarketRegimeEvent` in common. Schema compatibility test.
5. `ScheduledRegimeDetector` in risk-orchestrator (runs every 15 min). Unit test with mocks.
6. Flyway V40 + `MarketRegimeRepository`. Integration test.
7. `RegimeParameterProvider` interface + `AdaptiveRegimeParameterProvider`. Unit tests: each regime maps to correct VaR params.
8. Wire into `VaRCalculationService`. Unit tests: CRISIS → Monte Carlo + CL_99.
9. Kafka topic + `KafkaRegimeEventPublisher`. Integration test.
10. notification-service: `RegimeChangeRule`. Unit test.
11. Routes + gateway proxy. Acceptance tests.
12. UI: `RegimeIndicator` in header. Vitest + Playwright.
13. UI: Regime detail panel + signal panel. Playwright E2E.
14. UI: Regime history chart overlaid on VaR. Playwright E2E.

### Critical Test Cases

- Classifier correctly identifies March 2020 and September 2008 as CRISIS when back-tested.
- Correlation shift alert fires 5-10 trading days before crisis is obvious in prices.
- Regime switch to CRISIS increases VaR by 30%+ due to stressed parameters.
- Debounce: single vol spike does not trigger regime change; requires N consecutive observations.
- LSTM produces NaN (data gap): system falls back to EWMA vol, does not propagate NaN.
- VIX/credit spread data unavailable: fall back to two-factor model (vol + correlation), flag as degraded.
- Regime oscillation test: rapid alternating signals do not cause thrashing (debounce holds).
- Model staleness: >90 days since retrain → warning emitted.

### Risks

- **Rule-based first, ML second.** LSTM and IsolationForest trained on simulated data. Start with rules, add ML after validation against real historical crises.
- **Regime switching frequency.** Hysteresis filter: require N consecutive observations before switching. Exception: crisis → normal can be faster.
- **Compute cost in CRISIS.** Monte Carlo VaR is ~10× more expensive. Add Prometheus metric `var_calculation_regime_override_total`. Alert if MC running >50% of time.

---

## Cross-Cutting Infrastructure

### Shared Components (build once, use across directions)

| Component | Used By | Location |
|-----------|---------|----------|
| `PnlTickerStrip` | Dir 1, 4, 8 | `ui/src/components/PnlTickerStrip.tsx` |
| `LiquidityScoreBar` | Dir 3, 7 | `ui/src/components/LiquidityScoreBar.tsx` |
| `RegimeIndicator` | Dir 9, all tabs | `ui/src/components/RegimeIndicator.tsx` |
| `HierarchyContributionTable` | Dir 8 | `ui/src/components/HierarchyContributionTable.tsx` |
| `FactorAttributionChart` | Dir 4, 1 | `ui/src/components/FactorAttributionChart.tsx` |
| `RightDrawer` | Dir 2 (HedgePanel), existing WhatIfPanel | `ui/src/components/ui/RightDrawer.tsx` |
| `MiniSparkline` | Dir 1, 3, 9 | `ui/src/components/MiniSparkline.tsx` |

### Test Infrastructure Needed

- **Clock injection across services.** All services calling `Instant.now()` need a `Clock` parameter for temporal edge case testing.
- **WebSocket test helpers.** Extract `injectWebSocket(page, messages)` into `fixtures.ts` from existing `websocket-reconnect.spec.ts`.
- **Gatling WebSocket scenarios.** Existing simulations are HTTP-only. Add `PnlStreamSimulation.kt`.
- **Schema compatibility tests** for every new Kafka event type before first publish.
- **Golden-file tests** for new calculations (intraday P&L, LVaR, CVA, factor attribution).

### Global Risks

- **Risk engine thread pool.** Currently 10 workers. Directions 3, 4, 7, 9 each add gRPC methods. Increase to 20 workers initially; monitor queue depth.
- **Flyway migration count** reaches V40+. Enforce no `CREATE INDEX CONCURRENTLY` in all migration reviews.
- **Kafka topic proliferation.** 16 → ~25 topics. Ensure distinct `group.id` per consumer.
- **Redis as hot dependency.** Directions 1, 2, 4, 9 all use Redis. Every new Redis usage must follow the `InMemoryVaRCache` fallback pattern.
- **Proto backward compatibility.** All additions use new field numbers. buf lint/breaking CI catches issues.

### Shared Data Feeds (build once, serve multiple directions)

| Data Feed | Built For | Also Used By |
|-----------|-----------|--------------|
| ADV + bid-ask spreads | Dir 3 | Dir 2, 5, 7 |
| Credit spread / CDX series | Dir 4 | Dir 7, 9 |
| Factor return series | Dir 4 | Dir 6, 8 |
| `intraday_pnl_snapshots` | Dir 1 | Dir 8, 9 |
| `book_hierarchy` mapping | Dir 8 | Dir 2, 3, 7 |
| `counterparty_master` | Dir 7 | Dir 5 |
| `market_regime_state` | Dir 9 | Dir 4, 6, 8 |
