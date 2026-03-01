# Risk Tab Evolution Plan — V2

*Created: 2026-02-28*
*Follows completion of [risk-ui.md](risk-ui.md) (Phases 1–8 all shipped)*

---

## Current State (Post V1)

The Risk tab now contains:

1. **RiskAlertBanner** — severity-styled alert bar at the top with dismiss, relative timestamps, max 3 with overflow.
2. **VaR Dashboard** — compact card gauge (VaR + ES + PV + limit utilisation bar), Greeks heatmap by asset class, component breakdown donut, VaR/Greeks trend chart toggle with brush-to-zoom, What-If button, Refresh button.
3. **PositionRiskTable** — sortable by any numeric column, default `|varContribution|` desc, colour-coded concentration (% of Total), collapsible, error-state aware.
4. **PnlSummaryCard + StressSummaryCard** — side-by-side cards. P&L shows baseline-aware states and Greek decomposition. Stress shows last-run scenario with Run/View Details actions.
5. **JobHistory** — time range selector, job timechart, search, paginated table.

---

## Design Principles

Carried forward from V1:

- **Close the workflow loop.** Monitor → Investigate → Decide → Act.
- **Surface, don't duplicate.** Compact summaries with links — not rebuilt full components.
- **Earn every pixel.** Dense is fine; cluttered is not.

---

## Phase 9 — Add Theta to Greeks Trend Chart

**Priority: High — missing from the intraday Greeks view.**

**Trader question:** *"It's 3pm and I'm running an options book — how much theta am I bleeding?"*

### What to change

The `GreeksTrendChart` currently plots three series (delta, gamma, vega). Add a fourth line for theta. Theta is the other Greek that matters intraday — an options trader needs to see time decay bleeding away in real time throughout the day.

### Implementation

- Extend the `SERIES` array in `GreeksTrendChart.tsx` with `{ key: 'theta', color: '#f59e0b', label: 'Theta' }`.
- Extend `VaRHistoryEntry` in `useVaR.ts` with an optional `theta` field.
- Accumulate theta in the `aggregateGreeks()` helper alongside delta/gamma/vega.
- Update the tooltip to include the theta value.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/GreeksTrendChart.tsx` | Modify — add theta series |
| `ui/src/components/GreeksTrendChart.test.tsx` | Modify — add theta test cases |
| `ui/src/hooks/useVaR.ts` | Modify — extend `VaRHistoryEntry` with `theta`, accumulate in `aggregateGreeks` |

---

## Phase 10 — Wire "View Full Attribution" Link

**Priority: High — dead links undermine trust in the system.**

**Trader question:** *"I clicked View Full Attribution and nothing happened."*

### What to change

The `PnlSummaryCard` renders a "View Full Attribution" button with `<ExternalLink>` icon but has no `onClick` handler. This must navigate the user to the P&L tab.

### Implementation

- Accept an `onViewFullAttribution` callback prop on `PnlSummaryCard`.
- Wire it from `RiskTab` → `App.tsx` to switch the active tab to `pnl`.
- If no P&L data exists, the link should not render (it already hides when `showData` is false).

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/PnlSummaryCard.tsx` | Modify — accept and wire `onViewFullAttribution` prop |
| `ui/src/components/PnlSummaryCard.test.tsx` | Modify — test click callback |
| `ui/src/components/RiskTab.tsx` | Modify — accept and pass tab-switch callback |
| `ui/src/App.tsx` | Modify — pass `setActiveTab('pnl')` as callback |

---

## Phase 11 — Multi-Scenario Stress Results

**Priority: High — one scenario is not enough to make decisions.**

**Trader question:** *"If vol spikes or rates move or equities crash — what are all the outcomes?"*

### What to change

The `StressSummaryCard` currently renders a single scenario row. When a multi-scenario stress test is run, the card should show the top 3 worst scenarios by P&L impact, not just one.

### Implementation

- Change `StressSummaryCard` to accept `results: StressTestResultDto[]` (array) instead of `result: StressTestResultDto | null`.
- Sort by `|pnlImpact|` descending, display the top 3.
- If more than 3 scenarios exist, show a "View all N scenarios" link to the Scenarios tab.
- If only one scenario exists, render it as today (no regression).

### Data source consideration

- The `useStressTest` hook may need to support running multiple named scenarios and returning an array. If the backend already supports batch scenarios, wire the array through. If not, accumulate results from sequential runs client-side.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/StressSummaryCard.tsx` | Modify — accept array, render top 3 |
| `ui/src/components/StressSummaryCard.test.tsx` | Modify — multi-scenario test cases |
| `ui/src/components/RiskTab.tsx` | Modify — pass array of results |
| `ui/src/App.tsx` | Modify — if needed, change stress state shape |

---

## Phase 12 — Global "Last Updated" Timestamp

**Priority: High — stale risk numbers are worse than no risk numbers.**

**Trader question:** *"When was this entire view last refreshed?"*

### What to build

A single timestamp displayed prominently at the top-right of the Risk tab (or just below the alert banner) showing the most recent refresh time across all data sources. Format: `Last refreshed: 14:32:15` with a relative indicator (`2 min ago`) that turns amber after 5 minutes and red after 15 minutes.

### Implementation

- Track the latest refresh timestamp across `useVaR`, `usePositionRisk`, `useAlerts`.
- Use the most recent `calculatedAt` from VaR as the primary signal.
- Render a `LastUpdatedIndicator` component with staleness colouring.
- During March 2020, the systems that failed traders were the ones showing 10-minute-old Greeks as if they were live. This feature prevents that.

### Files to create/modify

| File | Action |
|---|---|
| `ui/src/components/LastUpdatedIndicator.tsx` | Create — staleness-aware timestamp display |
| `ui/src/components/LastUpdatedIndicator.test.tsx` | Create — tests for staleness thresholds |
| `ui/src/components/RiskTab.tsx` | Modify — compute latest timestamp, render indicator |

---

## Phase 13 — Portfolio Total Row in Greeks Heatmap

**Priority: High — the net matters more than the parts.**

**Trader question:** *"What's my total portfolio delta?"*

### What to change

The `RiskSensitivities` component shows delta/gamma/vega broken down by asset class, but does not show a portfolio-level total row. When managing a multi-asset book, the first thing a PM looks at is the net portfolio delta — not individual asset class deltas. Every Bloomberg PORT screen and Axioma output has a total row.

### Implementation

- Compute totals by summing across all `assetClassGreeks` entries.
- Render a bold total row at the bottom of the Greeks heatmap table with `font-semibold` styling and a top border to visually separate it.
- The `GreeksResultDto` may already carry portfolio-level totals — check and use those if available, otherwise compute client-side.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/RiskSensitivities.tsx` | Modify — add total row |
| `ui/src/components/RiskSensitivities.test.tsx` | Modify — test total row rendering |

---

## Phase 14 — Position Drill-Down

**Priority: Medium — completes the "investigate" step.**

**Trader question:** *"AAPL is contributing $800 VaR — which trades are driving that?"*

### What to build

Clicking a row in the `PositionRiskTable` should expand an inline detail view or navigate to a position detail panel showing the individual trades that make up that position. The workflow is always: see the concentration → investigate it → decide what to hedge. Right now the "investigate" step is missing.

### Implementation options

**Option A (inline expansion):** Add an expandable row to `PositionRiskTable` that fetches and displays trades for the selected instrument. Simpler, keeps context.

**Option B (side panel / modal):** Open a slide-out panel with full trade details, similar to `WhatIfPanel`. Richer, but takes focus away from the table.

Recommend Option A for the first iteration.

### Data source

- Requires a new API endpoint: `GET /api/v1/risk/positions/{portfolioId}/{instrumentId}/trades` — or the existing position data may already include trade-level granularity.
- Investigate what the backend provides before building.

### Files to create/modify

| File | Action |
|---|---|
| `ui/src/components/PositionRiskTable.tsx` | Modify — add row expansion with trade detail |
| `ui/src/components/PositionRiskTable.test.tsx` | Modify — test expansion behaviour |
| `ui/src/hooks/usePositionRisk.ts` | Modify — possibly add trade-level fetch |
| Backend | Investigate — may need new endpoint |

---

## Phase 15 — Theta and Rho in Position Risk Table

**Priority: Medium — missing Greeks at position level.**

**Trader question:** *"Which positions are bleeding the most theta? Where's my rates exposure?"*

### What to change

The `PositionRiskTable` shows delta, gamma, and vega per position but omits theta and rho. For a rates book or an options book, these matter at the position level — a trader needs to see which positions are costing the most carry (theta) and which are most sensitive to rate moves (rho).

### Implementation

- Add `theta` and `rho` columns to the table.
- Add them to the `SortField` type and `COLUMNS` array.
- The `PositionRiskDto` may need backend extension to include `theta` and `rho` fields.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/PositionRiskTable.tsx` | Modify — add theta/rho columns |
| `ui/src/components/PositionRiskTable.test.tsx` | Modify — test new columns |
| `ui/src/types.ts` | Modify — extend `PositionRiskDto` if needed |
| Backend | May need to expose theta/rho in position risk response |

---

## Phase 16 — Diversification Benefit Display

**Priority: Medium — reveals hidden correlation risk.**

**Trader question:** *"How much natural hedging am I getting? If diversification benefit is shrinking, my correlations are rising and my risk is worse than the headline number."*

### What to build

Show the diversification benefit in the `ComponentBreakdown` section — the difference between the sum of individual asset class VaRs and the portfolio-level VaR. This number tells the PM how much natural hedging the portfolio construction is providing.

### Implementation

- Compute: `diversificationBenefit = sum(component VaRs) - portfolioVaR`.
- Display below the donut chart: `Diversification: -$42,300 (17%)`.
- Colour-code: if benefit is shrinking (vs prior period), show amber warning — correlations may be rising.
- Optionally show a historical trend of diversification benefit over time.

### Data source

- `ComponentBreakdownDto` already has `varContribution` per asset class.
- Portfolio-level VaR is available from `VaRResultDto.varValue`.
- No new API needed for the basic calculation.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/ComponentBreakdown.tsx` | Modify — compute and display diversification benefit |
| `ui/src/components/ComponentBreakdown.test.tsx` | Modify — test diversification calculation |
| `ui/src/components/VaRDashboard.tsx` | Modify — pass portfolio VaR to ComponentBreakdown |

---

## Proposed Risk Tab Layout (After V2)

```
┌─────────────────────────────────────────────────────────┐
│ [RiskAlertBanner]                 Last refreshed: 14:32 │  Ph 12
├─────────────────────────────────────────────────────────┤
│ ┌──────────┐ ┌──────────────────────┐ ┌──────────────┐ │
│ │ VaR Card │ │ Greeks Heatmap       │ │ Component    │ │
│ │ ES, PV   │ │ (by asset class)     │ │ Breakdown    │ │
│ │ Limit %  │ │ ──────────────────── │ │ (donut)      │ │
│ └──────────┘ │ TOTAL  ΣΔ  ΣΓ  ΣV   │ │ Div: -$42K   │ │  Ph 13, 16
│              └──────────────────────┘ └──────────────┘ │
│                                                         │
│ [Time Range]  [VaR/ES ◉ | Greeks ○]                    │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Trend Chart (VaR/ES or Greeks incl. Theta)          │ │  Ph 9
│ └─────────────────────────────────────────────────────┘ │
│ Calc type · timestamp          [What-If] [Refresh]      │
├─────────────────────────────────────────────────────────┤
│ Position Risk Table (+ theta, rho columns)         [▾]  │  Ph 15
│ Instrument | ... | Δ | Γ | Ʋ | Θ | ρ | VaR | ES% │    │
│ ▶ AAPL  [expandable trade detail]                       │  Ph 14
├─────────────────────────────────────────────────────────┤
│ ┌─────────────────────┐  ┌────────────────────────────┐ │
│ │ P&L Summary          │  │ Stress Scenario Summary    │ │
│ │ Delta/Gamma/Vega/... │  │ Top 3 scenarios + impact   │ │  Ph 11
│ │ [View Full → P&L tab]│  │ [Run] [View Details →]     │ │  Ph 10
│ └─────────────────────┘  └────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│ Valuation Jobs                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Implementation Order

| Phase | Summary | Effort | Backend Changes | Depends On |
|---|---|---|---|---|
| 9. Theta in Greeks Chart | Add theta line to GreeksTrendChart | Small | None | — |
| 10. Wire P&L Link | Connect "View Full Attribution" to P&L tab | Small | None | — |
| 11. Multi-Scenario Stress | Show top 3 scenarios in StressSummaryCard | Medium | Possible (batch API) | — |
| 12. Last Updated Timestamp | Global staleness indicator | Small | None | — |
| 13. Portfolio Total Greeks | Add total row to RiskSensitivities | Small | None | — |
| 14. Position Drill-Down | Expandable trade detail in PositionRiskTable | Medium | Likely (trade-level API) | — |
| 15. Theta/Rho in Position Table | Add columns to PositionRiskTable | Small | Likely (extend DTO) | — |
| 16. Diversification Benefit | Show div benefit in ComponentBreakdown | Small | None | — |

Phases 9, 10, 12, 13, 16 are quick wins with no backend dependency — can be done in parallel.
Phases 11, 14, 15 may require backend changes — investigate first.

---

## Out of Scope (Carried Forward + New)

Carried from V1:

- **Multi-dimensional concentration views** (by sector, geography, currency) — requires new backend aggregation.
- **VaR backtesting overlay** (breach count vs predicted) — requires historical breach tracking.
- **Alert acknowledgement workflow** — requires backend state for acknowledged/unacknowledged.
- **Position liquidity scoring** — requires new data source (ADV, bid-ask spread history).
- **Correlation / basis risk analysis** — requires correlation matrix computation and a dedicated view.

New from V2 review:

- **Historical diversification benefit trend** — track div benefit over time to detect rising correlations.
- **Cross-tab navigation from stress scenarios** — clicking a stressed position should jump to that position in the risk table.
- **Intraday theta decay curve** — overlay theoretical theta decay curve on the Greeks trend chart.
