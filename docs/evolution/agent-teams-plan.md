# Trader Feedback — Execution Plan

*Generated: 2026-02-28*
*Team: architect (Elena), trader (Marcus), data-analyst (Priya), ux-designer (Ava)*

---

## Overview

This plan addresses the top 3 priorities from Marcus's trader feedback, in dependency order:

1. **Position-level risk columns** — delta, gamma, vega, VaR contribution per position in the Positions grid
2. **P&L attribution** — daily breakdown by greek/factor (delta, gamma, vega, theta, rho, unexplained)
3. **What-if / pre-trade analysis** — simulate a trade and see impact on portfolio risk

**Dependency chain:** Priority 1 is the foundation (position-level risk data model). Priorities 2 and 3 depend on it but are independent of each other.

---

## Phase 1: Position-Level Risk Columns

### Step 1.1 — Backend: PositionRisk domain model and allocation logic
- [x] **Done** (commit d1009b2)

**What:** Create the `PositionRisk` domain model and refactor the existing pro-rata allocation logic in `VaRCalculationService.computePositionBreakdown()` from raw JSON into proper domain objects.

**Files to create:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/PositionRisk.kt`

**Files to modify:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/ValuationResult.kt` — add `positionRisk: List<PositionRisk>` field
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/service/VaRCalculationService.kt` — refactor `computePositionBreakdown()` to return `List<PositionRisk>` and include in `ValuationResult`

**Allocation methodology (from Priya):**
- Market-value-weighted pro-rata (Euler allocation): `position_var = asset_class_var * (|position_mv| / sum(|position_mv|))`
- Use absolute market value for weighting so shorts contribute proportionally
- Greeks allocation is also pro-rata within asset class (acceptable since delta/gamma/vega are extensive quantities)

**TDD tests to write first:**
- `PositionRiskTest.kt` — domain model construction
- `VaRCalculationServiceTest.kt` — test that `calculateVaR` produces `positionRisk` in the result
- `VaRCalculationServiceTest.kt` — test pro-rata allocation sums to total (within rounding tolerance)
- `VaRCalculationServiceTest.kt` — test with long and short positions in same asset class

---

### Step 1.2 — Backend: PositionRisk DTO, mapper, and API endpoint
- [x] **Done** (commit 47da2bd)

**What:** Create the DTO, mapper, and REST endpoint to expose position-level risk data.

**Files to create:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/PositionRiskDto.kt`

**Files to modify:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/VaRResultResponse.kt` — add optional `positionRisk: List<PositionRiskDto>?`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskRoutes.kt` — add `GET /api/v1/risk/positions/{portfolioId}`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskMappers.kt` — add `PositionRisk.toDto()` mapping

**PositionRiskDto fields:**
```
instrumentId, assetClass, marketValue, delta, gamma, vega,
varContribution, esContribution, percentageOfTotal
```

**TDD tests to write first:**
- `PositionRiskMapperTest.kt` — test domain-to-DTO mapping
- `RiskRoutesAcceptanceTest.kt` — test `GET /api/v1/risk/positions/{portfolioId}` returns position-level data
- Verify sum of `percentageOfTotal` equals 100% within tolerance

---

### Step 1.3 — Frontend: Types, API function, and usePositionRisk hook
- [ ] **Done**

**What:** Add TypeScript types for position risk data, API function to fetch it, and a hook to manage the data.

**Files to modify:**
- `ui/src/types.ts` — add `PositionRiskDto` interface

**Files to create:**
- `ui/src/hooks/usePositionRisk.ts` — fetches position risk, merges with position data, auto-refreshes

**Files to modify:**
- `ui/src/api/risk.ts` — add `fetchPositionRisk(portfolioId)` function

**TDD tests to write first:**
- `ui/src/api/risk.test.ts` — test `fetchPositionRisk` API function
- `ui/src/hooks/usePositionRisk.test.tsx` — test hook fetches and returns data, handles loading/error states

---

### Step 1.4 — Frontend: Risk columns in PositionGrid
- [ ] **Done**

**What:** Add delta, gamma, vega, VaR contribution, and % of portfolio VaR columns to the positions table. Add summary cards for Portfolio Delta and Portfolio VaR.

**Files to modify:**
- `ui/src/components/PositionGrid.tsx` — add risk columns with two-row grouped header ("Position Details" | "Risk Metrics")
- `ui/src/App.tsx` — wire `usePositionRisk` hook and pass data to `PositionGrid`

**UX details (from Ava):**
- Column order: Instrument, Asset Class, Qty, Avg Cost, Mkt Price, Mkt Value, P&L | Delta, Gamma, Vega, VaR Contrib %
- Risk Metrics group header with `bg-indigo-50` tint
- VaR contribution shows percentage with inline horizontal bar
- Delta colored green/red based on sign; gamma/vega neutral color
- All risk columns sortable (chevron icon on active sort column)
- When risk data unavailable, show "—" with tooltip (not zero)
- Progressive loading: position columns render immediately, risk columns show spinner until data arrives
- Summary cards: add Portfolio Delta and Portfolio VaR to the top strip

**TDD tests to write first:**
- `PositionGrid.test.tsx` — test new columns render when positionRisk is provided
- `PositionGrid.test.tsx` — test grid renders correctly when positionRisk is absent (graceful degradation)
- `PositionGrid.test.tsx` — test sorting by VaR contribution
- `PositionGrid.test.tsx` — test "—" displayed when risk data is null for a position

---

## Phase 2: P&L Attribution

### Step 2.1 — Backend: PnlAttribution domain models and service
- [ ] **Done**

**What:** Create domain models and a pure computation service for P&L attribution using Taylor expansion.

**Files to create:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/PnlAttribution.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/PositionPnlAttribution.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/service/PnlAttributionService.kt`

**P&L attribution formulas (from Priya):**
```
deltaPnl   = delta * dS           (where dS = price change)
gammaPnl   = 0.5 * gamma * dS²
vegaPnl    = vega * dVol          (implied vol change)
thetaPnl   = theta * dt           (dt = 1/252 for one trading day)
rhoPnl     = rho * dr             (risk-free rate change)
unexplained = totalPnl - sum(explained components)
```

**Important:** Use beginning-of-day (BOD) greeks for attribution (yesterday's close greeks), not current greeks, to avoid look-ahead bias.

**TDD tests to write first:**
- `PnlAttributionServiceTest.kt` — test Taylor expansion decomposition with known inputs/outputs
- `PnlAttributionServiceTest.kt` — test unexplained = total - sum(explained)
- `PnlAttributionServiceTest.kt` — test with zero greeks (all goes to unexplained)
- `PnlAttributionServiceTest.kt` — test with zero price change (only theta contributes)
- `PnlAttributionServiceTest.kt` — test position-level attributions sum to portfolio total

---

### Step 2.2 — Backend: Database tables, snapshot job, and API endpoint
- [ ] **Done**

**What:** Add database tables for daily risk snapshots and P&L attribution history. Create a snapshot service and REST endpoints.

**Database tables:**
- `daily_risk_snapshots` — stores BOD positions + greeks per instrument per day
- `pnl_attributions` — stores computed P&L attribution per portfolio per day

**Files to create:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/repository/DailyRiskSnapshotRepository.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/repository/PnlAttributionRepository.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/PnlAttributionResponse.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/PositionPnlAttributionDto.kt`
- DB migration file for new tables

**Files to modify:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskRoutes.kt` — add `GET /api/v1/risk/pnl-attribution/{portfolioId}`

**TDD tests to write first:**
- `DailyRiskSnapshotRepositoryIntegrationTest.kt` — test snapshot persistence and retrieval
- `PnlAttributionRepositoryIntegrationTest.kt` — test attribution persistence and retrieval
- `RiskRoutesAcceptanceTest.kt` — test P&L attribution endpoint returns correct structure

---

### Step 2.3 — Frontend: Types, API, hook, and P&L tab
- [ ] **Done**

**What:** Add the P&L tab to the application with waterfall chart and attribution table.

**Files to modify:**
- `ui/src/types.ts` — add `PnlAttributionDto`, `PositionPnlAttributionDto` interfaces
- `ui/src/App.tsx` — add `'pnl'` tab between Positions and Risk (icon: `TrendingUp`)

**Files to create:**
- `ui/src/api/pnlAttribution.ts` — `fetchPnlAttribution(portfolioId)` function
- `ui/src/hooks/usePnlAttribution.ts` — manages P&L attribution data fetching
- `ui/src/components/PnlTab.tsx` — thin orchestrator (following RiskTab pattern)
- `ui/src/components/PnlWaterfallChart.tsx` — horizontal waterfall chart showing factor contributions
- `ui/src/components/PnlAttributionTable.tsx` — factor breakdown table with position drill-down

**UX details (from Ava):**
- New top-level tab: P&L (positioned between Positions and Risk)
- Top panel (60%): horizontal waterfall chart — delta, gamma, vega, theta, rho, unexplained, total
- Bottom panel (40%): attribution detail table with clickable factor rows for position drill-down
- Factor colors: Delta=#3b82f6, Gamma=#8b5cf6, Vega=#a855f7, Theta=#f59e0b, Rho=#22c55e, Unexplained=#9ca3af
- Date selector presets: Today | Yesterday | MTD | YTD
- Positive P&L green, negative red (matching existing `pnlColorClass`)

**TDD tests to write first:**
- `ui/src/api/pnlAttribution.test.ts` — test API function
- `ui/src/hooks/usePnlAttribution.test.tsx` — test hook data fetching
- `PnlTab.test.tsx` — test tab renders waterfall chart and attribution table
- `PnlWaterfallChart.test.tsx` — test chart renders factor bars
- `PnlAttributionTable.test.tsx` — test table rows and position drill-down

---

## Phase 3: What-If / Pre-Trade Analysis

### Step 3.1 — Backend: WhatIf domain models and analysis service
- [ ] **Done**

**What:** Create domain models for hypothetical trades and a service that applies them to the current portfolio and runs comparative VaR calculations.

**Files to create:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/HypotheticalTrade.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/WhatIfResult.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/service/WhatIfAnalysisService.kt`

**Position modification logic:**
- BUY existing instrument: increase quantity, recalculate weighted avg cost
- SELL existing instrument: decrease quantity (can go short)
- New instrument: create new position
- Close position (qty → 0): remove from portfolio

**Performance (from Priya):** Run base and hypothetical VaR calculations in parallel using Kotlin coroutines. Use cached base result if fresh (within 5-minute staleness window).

**Statistical correctness (from Priya):** Both calculations must use identical market data, vol assumptions, and correlation matrix. Fetch market data once, pass to both calls.

**TDD tests to write first:**
- `WhatIfAnalysisServiceTest.kt` — test applying BUY trade to existing position
- `WhatIfAnalysisServiceTest.kt` — test applying SELL trade reducing position
- `WhatIfAnalysisServiceTest.kt` — test applying SELL trade creating short position
- `WhatIfAnalysisServiceTest.kt` — test adding new instrument not in portfolio
- `WhatIfAnalysisServiceTest.kt` — test closing a position exactly (qty → 0)
- `WhatIfAnalysisServiceTest.kt` — test impact calculation (VaR change, delta change)
- `WhatIfAnalysisServiceTest.kt` — test multiple hypothetical trades (basket)

---

### Step 3.2 — Backend: WhatIf DTOs and API endpoint
- [ ] **Done**

**What:** Create request/response DTOs and the REST endpoint for what-if analysis.

**Files to create:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/WhatIfRequestBody.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/HypotheticalTradeDto.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/WhatIfResponse.kt`

**Files to modify:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskRoutes.kt` — add `POST /api/v1/risk/what-if/{portfolioId}`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskMappers.kt` — add what-if mapping functions

**Endpoint:** `POST /api/v1/risk/what-if/{portfolioId}`
```json
{
  "hypotheticalTrades": [
    { "instrumentId": "SPY", "assetClass": "EQUITY", "side": "SELL", "quantity": "500", "priceAmount": "450.00", "priceCurrency": "USD" }
  ],
  "calculationType": "PARAMETRIC",
  "confidenceLevel": "CL_95"
}
```

**Validation:** quantity > 0, price >= 0, valid asset class, valid side (BUY/SELL).

**TDD tests to write first:**
- `WhatIfMapperTest.kt` — test DTO-to-domain and domain-to-DTO mapping
- `RiskRoutesAcceptanceTest.kt` — test POST endpoint returns correct response structure
- `RiskRoutesAcceptanceTest.kt` — test validation errors (invalid quantity, missing fields)

---

### Step 3.3 — Frontend: Types, API, hook, and WhatIf drawer panel
- [ ] **Done**

**What:** Create the what-if analysis UI as a right-side sliding drawer accessible from the Positions tab.

**Files to modify:**
- `ui/src/types.ts` — add `HypotheticalTradeDto`, `WhatIfRequestDto`, `WhatIfResponseDto`, `WhatIfSnapshotDto`, `WhatIfImpactDto`
- `ui/src/App.tsx` — add What-If button to Positions tab header, manage drawer state

**Files to create:**
- `ui/src/api/whatIf.ts` — `runWhatIfAnalysis(portfolioId, request)` function
- `ui/src/hooks/useWhatIf.ts` — manages form state, submission, results
- `ui/src/components/WhatIfPanel.tsx` — the sliding drawer panel with trade input and results

**UX details (from Ava):**
- Right-side sliding drawer (`w-[420px]`), not a modal or tab
- Two entry points: "What-If" button in Positions header, or context action on a position row
- Trade input form: instrument (autocomplete), direction (Buy/Sell toggle), quantity, price (pre-filled with market price)
- Before/After comparison table: VaR, Delta, Gamma, Vega, ES with current/after/change columns
- Change column colored: VaR reduction = green, increase = red
- Arrow icons (ArrowDown green, ArrowUp red) for directional changes
- "Add another trade" for basket simulation
- Escape key closes drawer
- Drawer slide-in animation: `transition-transform duration-300`

**TDD tests to write first:**
- `ui/src/api/whatIf.test.ts` — test API function
- `ui/src/hooks/useWhatIf.test.tsx` — test hook manages form state and submission
- `WhatIfPanel.test.tsx` — test trade form renders with all inputs
- `WhatIfPanel.test.tsx` — test before/after comparison renders with results
- `WhatIfPanel.test.tsx` — test add/remove multiple trades
- `WhatIfPanel.test.tsx` — test drawer opens and closes

---

## Architectural Decisions (Requiring Approval)

These decisions were pre-approved by the team's analysis:

1. **P&L attribution calculated in risk-orchestrator (Kotlin)**, not risk-engine (Python) — it's a straightforward Taylor expansion on data the orchestrator already has
2. **New database tables** for daily_risk_snapshots and pnl_attributions in risk-orchestrator's database
3. **New P&L tab** in the UI navigation, positioned between Positions and Risk
4. **What-If panel** as a right-side drawer on the Positions tab
5. **Pro-rata (Euler) allocation** for position-level risk decomposition using absolute market value weighting

---

## Execution Protocol

For each step:
1. Write failing tests first (RED)
2. Implement minimal code to pass (GREEN)
3. Refactor while keeping tests green
4. Run full test suite
5. Commit and push
6. Mark step as done in this document
