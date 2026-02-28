# Risk Tab Evolution Plan

*Created: 2026-02-28*
*Updated: 2026-02-28 — revised Phase 5 after SOD baseline enhancements, re-prioritised*

---

## Current State

The Risk tab has two sections:

1. **VaR Dashboard** (top) — 4-column grid: VaR gauge, Greeks heatmap (2 cols), component breakdown donut. Below: time range selector, VaR/ES trend chart, calculation type + refresh button.
2. **Job History** (bottom) — time range selector, job timechart (stacked bar), search, paginated job table with expandable detail rows.

**Existing infrastructure not yet surfaced on the Risk tab:**

| Capability | API | Hook | Component | Status |
|---|---|---|---|---|
| Position-level risk (delta/gamma/vega, VaR/ES contribution) | `GET /api/v1/risk/positions/{id}` | `usePositionRisk` | — | API + hook exist, no Risk tab component |
| Stress test results | `POST /api/v1/risk/stress/{id}` | `useStressTest` | `StressTestPanel` | Lives on Scenarios tab only |
| P&L attribution (Greek-decomposed) | `GET /api/v1/risk/pnl/{id}` | `usePnlAttribution` | `PnlTab`, `PnlAttributionTable`, `PnlWaterfallChart` | Lives on P&L tab only |
| SOD baseline management | `POST /api/v1/risk/sod/{id}`, `DELETE`, `POST .../compute` | `useSodBaseline` | `SodBaselineIndicator`, `JobPickerDialog`, `ConfirmDialog` | Mature — supports create, reset, pick-from-history, compute-on-demand |
| What-if / pre-trade | `POST /api/v1/risk/what-if/{id}` | `useWhatIf` | `WhatIfPanel` (overlay) | Button on Positions tab only |
| Alert rules & events | `GET /api/v1/notifications/rules`, `/alerts` | `useNotifications` | `NotificationCenter` | Lives on Alerts tab only |
| FRTB regulatory capital | `POST /api/v1/risk/frtb/{id}` | `useRegulatory` | `RegulatoryDashboard` | Lives on Regulatory tab only |

---

## Design Principles

- **Close the workflow loop.** Monitor → Investigate → Decide → Act. The current Risk tab covers "Monitor". Each phase below extends towards "Investigate", "Decide", and "Act".
- **Surface, don't duplicate.** Where data already lives on another tab, show a compact summary with a link — don't rebuild the full component.
- **Earn every pixel.** Dense screens are fine; cluttered screens are not. Every addition must help answer a question a trader actually asks.

---

## Phase 1 — Position Risk Table

**Priority: Highest — this is the #1 gap.**

**Trader question:** *"VaR jumped — which positions are driving it?"*

### What to build

A `PositionRiskTable` component inserted between the VaR Dashboard and Job History sections. Columns:

| Instrument | Asset Class | Market Value | Delta | Gamma | Vega | VaR Contribution | ES Contribution | % of Total |
|---|---|---|---|---|---|---|---|---|

- Sorted by `|varContribution|` descending (largest risk drivers at top).
- Sortable columns (click header to toggle asc/desc).
- Colour-code the `% of Total` column: red above 30%, amber above 15%, neutral below.
- Collapsible — starts expanded, can be collapsed via a header toggle to save space once reviewed.
- Refreshes alongside the VaR dashboard (same polling cycle or on manual refresh).

### Data source

- Hook: `usePositionRisk(portfolioId)` — already exists.
- Type: `PositionRiskDto` — already has all required fields.
- No new API work needed.

### Files to create/modify

| File | Action |
|---|---|
| `ui/src/components/PositionRiskTable.tsx` | Create — new component |
| `ui/src/components/PositionRiskTable.test.tsx` | Create — tests |
| `ui/src/components/RiskTab.tsx` | Modify — wire `usePositionRisk`, render `PositionRiskTable` between dashboard and job history |
| `ui/src/components/RiskTab.test.tsx` | Modify — add test coverage |

---

## Phase 2 — Limit-Aware VaR Gauge

**Priority: High — the gauge currently measures the wrong thing.**

**Trader question:** *"Am I close to my limit?"*

### What to change

The VaR gauge currently colours based on VaR / (ES * 1.5) ratio. This tells the trader nothing operationally useful. Change it to:

1. Fetch the active VaR-type alert rule (if any) from the notifications API.
2. Use the rule's `threshold` as the gauge maximum.
3. Colour zones become: green (< 60% of limit), amber (60–85%), red (> 85%).
4. If no VaR alert rule exists, fall back to the current ES-based colouring.
5. Show the limit value as a label below the gauge: `Limit: $500,000`.

### Data source

- API: `GET /api/v1/notifications/rules` — filter for `type === 'VAR_BREACH'`.
- Hook: `useNotifications` already fetches rules. Pass the VaR rule threshold into `VaRGauge` as an optional prop.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Modify — accept optional `limit` prop, change colour logic |
| `ui/src/components/VaRGauge.test.tsx` | Modify — add limit-aware test cases |
| `ui/src/components/VaRDashboard.tsx` | Modify — accept and pass through `limit` prop |
| `ui/src/components/RiskTab.tsx` | Modify — fetch notifications, extract VaR limit, pass to dashboard |

---

## Phase 3 — Active Alert Banner

**Priority: High — breaches should be visible without navigating away.**

**Trader question:** *"Are any limits currently breached?"*

### What to build

A `RiskAlertBanner` component rendered at the very top of the Risk tab, above the VaR Dashboard. Only visible when there are recent unacknowledged alerts.

- Shows a compact red/amber bar: `"VaR BREACH — $523,000 exceeds $500,000 limit (triggered 2 min ago)"`.
- Multiple alerts stack vertically (max 3, with "View all" link to Alerts tab).
- Each alert has a dismiss button (client-side only — doesn't affect the Alerts tab state).
- Polls on the same interval as the notifications hook or listens to the existing alert data.

### Data source

- API: `GET /api/v1/notifications/alerts?limit=5` — already exists.
- The `AlertEventDto` has `severity`, `message`, `currentValue`, `threshold`, `triggeredAt`.

### Files to create/modify

| File | Action |
|---|---|
| `ui/src/components/RiskAlertBanner.tsx` | Create — new component |
| `ui/src/components/RiskAlertBanner.test.tsx` | Create — tests |
| `ui/src/components/RiskTab.tsx` | Modify — fetch recent alerts, render banner at top |

---

## Phase 4 — Stress Scenario Summary Card

**Priority: Medium — completes the "what could go wrong" picture.**

**Trader question:** *"If vol spikes or rates move, how bad does it get?"*

### What to build

A `StressSummaryCard` component below the VaR Dashboard (same row as the position risk table or as a collapsible card above it). Shows the last-run stress test results in compact form:

| Scenario | Base VaR | Stressed VaR | P&L Impact |
|---|---|---|---|
| Equity Crash -20% | $200K | $580K | -$1.2M |
| Rates +100bp | $200K | $310K | -$420K |
| Vol Spike +10pts | $200K | $490K | -$890K |

- Colour-code P&L Impact (red for losses).
- "Run Stress Tests" button that triggers the stress test API.
- "View Details" link to the Scenarios tab.
- If no stress tests have been run yet, show a CTA: "No stress results — run scenarios to see impact."

### Data source

- Hook: `useStressTest` — already exists on the Scenarios tab. Reuse or lift state.
- Type: `StressTestResultDto` — has all required fields.

### Design consideration

The stress test hook currently lives in `ScenariosTab`. Two options:
1. **Lift state to App** and pass results down to both tabs (preferred if both tabs need to stay in sync).
2. **Independent fetch** on the Risk tab — simpler but results may diverge if scenarios are re-run on one tab.

Recommend option 1.

### Files to create/modify

| File | Action |
|---|---|
| `ui/src/components/StressSummaryCard.tsx` | Create — compact stress results |
| `ui/src/components/StressSummaryCard.test.tsx` | Create — tests |
| `ui/src/components/RiskTab.tsx` | Modify — render stress summary |
| `ui/src/App.tsx` | Modify — lift `useStressTest` state, pass to both Risk and Scenarios tabs |

---

## Phase 5 — P&L Attribution Summary

**Priority: Medium-High — connects "where am I" to "why did it move". Effort reduced by recent SOD baseline enhancements.**

**Trader question:** *"VaR changed overnight — was it a delta move, a vol move, or theta bleed?"*

### What to build

A `PnlSummaryCard` component that shows today's P&L decomposition in compact form:

```
Today's P&L: +$45,200
  Delta: +$62,100  Gamma: -$8,400  Vega: +$12,300
  Theta: -$18,900  Rho: -$1,900  Unexplained: $0
```

- Horizontal bar segments (waterfall-style) showing each Greek's contribution.
- Green for positive, red for negative contributions.
- "View Full Attribution" link to the P&L tab.
- **Baseline-aware states:**
  - **No baseline:** Show contextual message with baseline metadata if previously set — e.g. "No SOD baseline — last set from job `a3f2c91d` (MONTE_CARLO) at 06:15". Link to P&L tab to create one.
  - **Baseline exists, no attribution computed:** Show "SOD baseline active — compute P&L attribution" with a compute button.
  - **Attribution available:** Show the compact factor breakdown.

### Data source

- Hook: `useSodBaseline(portfolioId)` — **fully mature**. Now provides:
  - `status.sourceJobId` — the VaR job used as baseline anchor
  - `status.calculationType` — PARAMETRIC / HISTORICAL / MONTE_CARLO
  - `computeAttribution()` — triggers attribution computation and returns `PnlAttributionDto`
  - `createSnapshot(jobId?)` — creates baseline (optionally from a specific job)
  - `resetBaseline()` — clears the baseline
- Hook: `usePnlAttribution(portfolioId)` — fetches existing attribution data.
- Type: `PnlAttributionDto` — has full Greek decomposition (`deltaPnl`, `gammaPnl`, `vegaPnl`, `thetaPnl`, `rhoPnl`, `unexplainedPnl`).
- **No new hook or API work needed.** All infrastructure is battle-tested on the P&L tab.

### Design note

This card is **read-only with a compute trigger** — it does not need to replicate the full SOD management workflow (create, reset, pick-from-history, confirm dialog). That complexity lives on the P&L tab where it belongs. The Risk tab card just shows the numbers and links out for management actions.

### Files to create/modify

| File | Action |
|---|---|
| `ui/src/components/PnlSummaryCard.tsx` | Create — compact P&L attribution with baseline-aware states |
| `ui/src/components/PnlSummaryCard.test.tsx` | Create — tests |
| `ui/src/components/RiskTab.tsx` | Modify — wire `useSodBaseline` and `usePnlAttribution`, render card |

---

## Phase 6 — What-If Entry Point

**Priority: Medium — closes the "decide and act" loop.**

**Trader question:** *"If I hedge my delta with 500 SPY puts, what happens to my VaR?"*

### What to build

A "What-If" button on the Risk tab (next to the existing Refresh button) that opens the `WhatIfPanel` overlay. The panel already exists and works — it just isn't accessible from the Risk tab.

### What to change

- Add a `FlaskRound` icon button in the VaR Dashboard footer bar, next to the Refresh button.
- Wire it to the same `whatIfOpen` state in `App.tsx`.
- Optionally: pass a callback so that when a what-if analysis completes, the Risk tab auto-refreshes.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRDashboard.tsx` | Modify — accept `onWhatIf` callback prop, render button |
| `ui/src/components/RiskTab.tsx` | Modify — pass what-if handler |
| `ui/src/App.tsx` | Modify — pass `setWhatIfOpen` to RiskTab (or lift to shared context) |

---

## Phase 7 — Greeks Trend Chart

**Priority: Lower — useful for pattern recognition over time.**

**Trader question:** *"My vega has been creeping up all week — is that intentional?"*

### What to build

A secondary trend chart (or toggle on the existing VaR trend chart) showing Greeks over time:

- Lines for portfolio-level delta, gamma, vega.
- Same brush-to-zoom, time range, and tooltip interactions as the VaR chart.
- Toggle between "VaR/ES" and "Greeks" views via a segmented control above the chart.

### Data source

This requires backend changes. The current VaR polling returns a single snapshot — Greeks history is not persisted.

**Option A (simple):** Accumulate Greeks in the `useVaR` hook's client-side history, similar to VaR history. Limited to the current session.

**Option B (robust):** Extend the job history API to include Greeks in the summary response. The `ValuationJobSummaryDto` would gain optional `delta`, `gamma`, `vega` fields populated from completed jobs.

Recommend starting with Option A and moving to Option B when the backend roadmap allows.

### Files to create/modify

| File | Action |
|---|---|
| `ui/src/components/GreeksTrendChart.tsx` | Create — new chart component |
| `ui/src/components/GreeksTrendChart.test.tsx` | Create — tests |
| `ui/src/hooks/useVaR.ts` | Modify — track Greeks history entries |
| `ui/src/components/VaRDashboard.tsx` | Modify — add chart toggle, render Greeks chart |

---

## Phase 8 — Compact VaR Card (Gauge Redesign)

**Priority: Lower — optimisation, not a blocker.**

**Trader question:** *"I need the number, not a pretty arc."*

### What to change

Replace the semi-circle SVG gauge with a compact vertical card:

```
┌─────────────────┐
│ VaR (95%)       │
│ $203,400    ●   │  ← green/amber/red dot
│                 │
│ ES   $287,100   │
│ PV   $12.4M     │
│ Limit  80.7%    │  ← utilisation bar
└─────────────────┘
```

- Same information in ~40% less vertical space.
- The freed space goes to the position risk table or stress summary.
- Limit utilisation as a thin horizontal progress bar (green/amber/red).
- Tooltips remain on info icons.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Rewrite — card layout instead of SVG gauge |
| `ui/src/components/VaRGauge.test.tsx` | Modify — update assertions for new layout |

---

## Proposed Risk Tab Layout (After All Phases)

```
┌─────────────────────────────────────────────────────────┐
│ [RiskAlertBanner — only if active breaches]              │  Phase 3
├─────────────────────────────────────────────────────────┤
│ ┌──────────┐ ┌──────────────────────┐ ┌──────────────┐ │
│ │ VaR Card │ │ Greeks Heatmap       │ │ Component    │ │  Phase 2, 8
│ │ ES, PV   │ │ (by asset class)     │ │ Breakdown    │ │
│ │ Limit %  │ │                      │ │ (donut)      │ │
│ └──────────┘ └──────────────────────┘ └──────────────┘ │
│                                                         │
│ [Time Range]  [VaR/ES ◉ | Greeks ○]                    │  Phase 7
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Trend Chart (VaR/ES or Greeks)                      │ │
│ └─────────────────────────────────────────────────────┘ │
│ Calc type · timestamp          [What-If] [Refresh]      │  Phase 6
├─────────────────────────────────────────────────────────┤
│ ┌─────────────────────┐  ┌────────────────────────────┐ │
│ │ P&L Summary          │  │ Stress Scenario Summary    │ │  Phase 4, 5
│ │ Delta/Gamma/Vega/... │  │ Top 3 scenarios + impact   │ │
│ │ [View Full →]        │  │ [Run] [View Details →]     │ │
│ └─────────────────────┘  └────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│ Position Risk Table                                [▾]  │  Phase 1
│ Instrument | Asset Class | MV | Δ | Γ | Ʋ | VaR | ES% │
│ ...                                                     │
├─────────────────────────────────────────────────────────┤
│ Valuation Jobs                                          │
│ [Time Range] [Search]                                   │
│ [Job Timechart]                                         │
│ [Job Table with expandable rows]                        │
└─────────────────────────────────────────────────────────┘
```

---

## Implementation Order

| Phase | Effort | Backend Changes | Depends On |
|---|---|---|---|
| 1. Position Risk Table | Small | None | — |
| 2. Limit-Aware Gauge | Small | None | — |
| 3. Active Alert Banner | Small | None | — |
| 4. Stress Summary Card | Medium | None (lift state) | — |
| 5. P&L Summary Card | Small | None | — (SOD baseline infra now mature) |
| 6. What-If Entry Point | Small | None | — |
| 7. Greeks Trend Chart | Medium | Optional (Option B) | — |
| 8. Compact VaR Card | Small | None | Phase 2 (limit-aware logic) |

Phases 1–3 and 5 can be done in parallel — Phase 5 effort dropped from Medium to Small after SOD baseline enhancements landed. Phase 8 should follow Phase 2 since it builds on the limit-aware logic.

---

## Out of Scope (Future Considerations)

These were flagged in trader feedback but are larger initiatives beyond Risk tab UI changes:

- **Multi-dimensional concentration views** (by sector, geography, currency) — requires new backend aggregation.
- **VaR backtesting overlay** (breach count vs predicted) — requires historical breach tracking.
- **Alert acknowledgement workflow** — requires backend state for acknowledged/unacknowledged.
- **Position liquidity scoring** — requires new data source (ADV, bid-ask spread history).
- **Correlation / basis risk analysis** — requires correlation matrix computation and a dedicated view.

---

## Changelog

### 2026-02-28 — Post-SOD baseline enhancements

**Changes since initial plan:**

The P&L tab received significant enhancements that affect Phase 5:

- `useSodBaseline` hook now exposes `sourceJobId`, `calculationType`, and `computeAttribution()` — the Risk tab summary card can compute attribution on-demand without new hook work.
- `SodBaselineIndicator` gained a "Pick from History" flow via `JobPickerDialog` — lets users select a specific completed VaR job as the SOD anchor.
- Reset workflow now shows baseline metadata (source job, calc type, timestamp) in a confirmation dialog.
- `SodBaselineStatusDto` extended with `sourceJobId: string | null` and `calculationType: string | null`.

**Impact on plan:**

- **Phase 5 effort reduced** from Medium to Small. All hooks and APIs are battle-tested; the card is now a thin read-only summary over existing infrastructure.
- **Phase 5 priority bumped** from Medium to Medium-High. The mature SOD system makes this a quick win that connects the Risk and P&L workflows.
- **Phases 1–4, 6–8 unaffected.** No changes touched the Risk tab, VaR gauge, position risk, alerts, stress tests, or what-if components.
- **Current State table updated** to include SOD baseline as available infrastructure.
