# Risk Tab Evolution Plan — V3

*Created: 2026-02-28*
*Follows completion of [risk-ui-v2.md](risk-ui-v2.md) (Phases 9–16 all shipped)*
*Based on live UI review by Marcus (trader advisor persona)*

---

## Current State (Post V2)

The Risk tab now contains:

1. **RiskAlertBanner** — severity-styled alert bar at top with dismiss, relative timestamps, max 3 with overflow.
2. **LastUpdatedIndicator** — staleness-aware timestamp at top-right (neutral < 5min, amber 5–15min, red > 15min).
3. **VaR Dashboard** — compact card gauge (VaR + ES + PV + limit utilisation bar), Greeks heatmap by asset class with portfolio total row, component breakdown donut with diversification benefit, VaR/Greeks trend chart toggle with brush-to-zoom, What-If button, Refresh button.
4. **PositionRiskTable** — sortable by any numeric column including theta/rho, default `|varContribution|` desc, colour-coded concentration (% of Total), collapsible, expandable drill-down rows, error-state aware.
5. **PnlSummaryCard + StressSummaryCard** — side-by-side cards. P&L shows baseline-aware states, Greek decomposition, and "View Full Attribution" link to P&L tab. Stress shows top 3 scenarios sorted by worst P&L impact with "View all N scenarios" overflow.
6. **JobHistory** — time range selector, job timechart, search, paginated table.

---

## Design Principles

Carried forward from V1/V2:

- **Close the workflow loop.** Monitor → Investigate → Decide → Act.
- **Surface, don't duplicate.** Compact summaries with links — not rebuilt full components.
- **Earn every pixel.** Dense is fine; cluttered is not.

New for V3:

- **Numbers must be trustworthy.** Inconsistent formatting, raw enum values, and excessive precision erode trust. Every number on screen must have consistent formatting, clear units, and appropriate precision.
- **Empty states must explain themselves.** "No data available" is never acceptable. Always tell the user *why* there's no data and *what they can do about it*.
- **Alerts must be human-readable.** System log messages have no place on a trader-facing screen.

---

## Phase 17 — Human-Readable Alert Messages

**Priority: Critical — traders will ignore unreadable alerts, which is dangerous.**

**Problem:** Alert messages currently display raw system strings like `PNL_THRESHOLD GREATER_THAN 250000.0 (current: 251954.53464219306)`. This is a system log, not a human-readable alert. The current value has 14 decimal places.

### What to build

Transform alert messages into plain-English sentences with properly formatted numbers.

### Implementation

- Create an `alertMessageFormatter` utility that parses the structured alert data and produces human-readable messages.
- Map alert types to templates:
  - `PNL_THRESHOLD GREATER_THAN` → "Daily P&L exceeded $250K limit — current: $251,955"
  - `VAR_THRESHOLD GREATER_THAN` → "VaR breached $1M limit — current: $1,050,510"
  - Other types follow the same pattern.
- Format all numbers with appropriate precision (round to nearest dollar for large values).
- Format portfolio names: `macro-hedge` → "Macro Hedge" or keep as-is but wrap in quotes.

### Files to modify

| File | Action |
|---|---|
| `ui/src/utils/alertMessageFormatter.ts` | Create — message formatting utility |
| `ui/src/utils/alertMessageFormatter.test.ts` | Create — test all alert type templates |
| `ui/src/components/RiskAlertBanner.tsx` | Modify — use formatter instead of raw `alert.message` |

---

## Phase 18 — Hide Empty Limit Bar

**Priority: High — misleading information is worse than no information.**

**Problem:** The VaR Limit utilisation bar shows "Limit 0%" when no limit is configured. This implies a limit exists and utilisation is zero, which is incorrect. It occupies prime screen real estate while conveying nothing useful.

### What to build

Hide the limit section entirely when no limit is configured, or show "No limit set" as a subtle text indicator.

### Implementation

- The `VaRGauge` component already has `hasLimit` logic (`varLimit != null && varLimit > 0`).
- Currently the limit bar still renders when `hasLimit` is false — the `{hasLimit && (...)}` guard already exists in the code, so this may be a data issue where `varLimit` is `0` rather than `null`.
- Verify the `useVarLimit` hook returns `null` (not `0`) when no limit is configured.
- If `varLimit === 0`, treat it the same as `null` (no limit).

### Files to modify

| File | Action |
|---|---|
| `ui/src/hooks/useVarLimit.ts` | Verify — ensure null is returned when no limit exists |
| `ui/src/components/VaRGauge.tsx` | Verify — confirm hasLimit guard works correctly |
| `ui/src/components/VaRGauge.test.tsx` | Add — test that limit bar is hidden when limit is 0 or null |

---

## Phase 19 — Consistent Asset Class Label Formatting

**Priority: High — inconsistency erodes trust.**

**Problem:** The Component Breakdown donut correctly formats asset class labels ("Equity", "Fixed Income") but the Greeks heatmap shows raw enum values ("EQUITY", "FIXED_INCOME"). These sit side by side on screen.

### What to build

Use consistent Title Case formatting for asset class labels across all components.

### Implementation

- Extract the `formatAssetClassLabel` function from `ComponentBreakdown.tsx` into a shared utility.
- Apply it in `RiskSensitivities.tsx` for the Greeks heatmap rows.
- Apply it in `PositionRiskTable.tsx` for the asset class column.

### Files to modify

| File | Action |
|---|---|
| `ui/src/utils/formatAssetClass.ts` | Create — shared formatting utility |
| `ui/src/utils/formatAssetClass.test.ts` | Create — test SCREAMING_SNAKE to Title Case |
| `ui/src/components/ComponentBreakdown.tsx` | Modify — import shared utility |
| `ui/src/components/RiskSensitivities.tsx` | Modify — format asset class labels |
| `ui/src/components/PositionRiskTable.tsx` | Modify — format asset class labels |

---

## Phase 20 — Fix Theta/Rho Formatting and Placement

**Priority: High — inconsistent precision and awkward layout.**

**Problem:** Theta shows 4 decimal places (`424.3970`) while Rho shows a different format (`-66,840.5294`). Both float below the heatmap table with no visual connection. The rho value is enormous (-66K) and gets no special attention despite being a massive rates sensitivity.

### What to build

Standardise theta/rho precision and integrate them into the Greeks heatmap table or give them proper visual weight.

### Implementation

Option A (preferred): Add Theta and Rho as columns in the heatmap total row, making them part of the same visual structure. Since they are portfolio-level (not per-asset-class), show them only in the Total row with dashes in the asset class rows.

Option B: Keep them below the table but match the formatting of the table values (2 decimal places, comma-separated, consistent font).

- Standardise to 2 decimal places for both.
- If Rho is genuinely -66K, consider colour-coding or flagging it — that's a significant rates exposure.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/RiskSensitivities.tsx` | Modify — fix precision, improve layout |
| `ui/src/components/RiskSensitivities.test.tsx` | Modify — update expected formatting |

---

## Phase 21 — Actionable Empty States

**Priority: High — "no data" messages must explain and guide.**

**Problem:** "No position risk data available" doesn't tell the user why or what to do. Multiple empty states across the tab have the same issue.

### What to build

Replace generic empty messages with contextual explanations and actions.

### Implementation

- **Position Risk Breakdown**: "No position risk data — positions will appear after the next VaR calculation" or "No positions in portfolio" (depending on root cause). Include a "Refresh" button.
- **Stress Test Summary**: Already reasonable ("Run a stress test to see the summary") — keep as-is.
- **Greeks Trend Chart (single data point)**: "Trend data requires at least 2 calculations. Current values shown in the table above." Don't render a trend chart with a single dot.
- **VaR Trend Chart (mostly empty)**: When data occupies less than 20% of the x-axis range, auto-adjust the visible range or show a note like "Data available from HH:MM".

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/PositionRiskTable.tsx` | Modify — contextual empty state |
| `ui/src/components/GreeksTrendChart.tsx` | Modify — handle single-point case |
| `ui/src/components/VaRTrendChart.tsx` | Modify — handle sparse data |

---

## Phase 22 — Consistent PV Formatting

**Priority: Medium — cross-component inconsistency.**

**Problem:** PV shows as `$155.8K` (compact) in the VaR gauge area but as `155,802.5` (raw, no dollar sign) in the Job History table. Users see both on the same page.

### What to build

Use consistent formatting for PV values across all components.

### Implementation

- Use the compact currency format (`$155.8K`) in summary/dashboard views.
- Use the full formatted currency (`$155,802.50`) in table/detail views.
- Both should include the `$` sign.
- Job History table PV column should show `$155,802.50` or `$155.8K` consistently.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/JobHistory.tsx` | Modify — format PV with currency symbol |

---

## Phase 23 — VaR Change Indicator

**Priority: Medium — essential for morning risk reviews.**

**Problem:** VaR shows $1,050.51 as a static number. There's no indication whether risk is up or down compared to prior period. Traders need to see direction and magnitude of change at a glance.

### What to build

Show the change from prior period next to the headline VaR number.

### Implementation

- Compute `varChange = currentVaR - previousVaR` using the history data already available in `useVaR`.
- Display: `$1,050.51 ↑ $32.40 (+3.2%)` or `$1,050.51 ↓ $15.00 (-1.4%)`.
- Colour-code: green for risk decrease, red for risk increase (note: VaR going *up* is bad, so up = red).
- "Previous" = the second-most-recent calculation in the history, or the first calculation of the current day (configurable later).

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Modify — add change indicator |
| `ui/src/components/VaRGauge.test.tsx` | Modify — test change display |
| `ui/src/hooks/useVaR.ts` | Modify — expose previous VaR value |

---

## Phase 24 — ES/VaR Ratio Display

**Priority: Medium — standard tail risk metric.**

**Problem:** ES and VaR are shown separately but their ratio (a key tail risk indicator) is not computed or displayed. An ES/VaR ratio of 1.1x means thin tails; 2x means fat tails and trouble.

### What to build

Show the ES/VaR ratio alongside the ES value.

### Implementation

- Compute `ratio = ES / VaR`.
- Display next to ES: `ES $1,317.38 (1.25x)`.
- Colour-code: neutral for ratio < 1.3, amber for 1.3–1.5, red for > 1.5.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Modify — add ratio display |
| `ui/src/components/VaRGauge.test.tsx` | Modify — test ratio calculation and colour |

---

## Phase 25 — Greek Units / Currency Labels

**Priority: Medium — ambiguous numbers are dangerous.**

**Problem:** Greeks table shows Delta -46.25, Gamma -151,503.63, Vega 2,839.38. There are no units. Delta of -46 *what*? The magnitude differences suggest different units, which is confusing.

### What to build

Add units or context to Greek column headers.

### Implementation

- Show currency in column headers: "Delta ($)" or "Delta (USD)".
- Alternatively, add a subtle note below the table: "All values in USD".
- Ensure all Greek values in the table use consistent decimal precision (2dp).

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/RiskSensitivities.tsx` | Modify — add units to headers |
| `ui/src/components/RiskSensitivities.test.tsx` | Modify — update header assertions |

---

## Phase 26 — Collapse Job History by Default

**Priority: Medium — wrong information priority.**

**Problem:** The Job History section (283 jobs, 29 pages) takes up massive screen real estate below the fold. It pushes P&L and Stress cards further down. Traders need to know: was the last calc successful, is one running now, did anything fail recently — not a full audit trail of every scheduled job.

### What to build

Option A (preferred): Collapse Job History by default. Show a one-line summary: "Last calc: 23:47 (success) · 283 jobs today". Click to expand.

Option B: Move Job History to the System tab entirely and add a compact "Calc Status" indicator to the Risk tab.

### Implementation (Option A)

- Add a collapsed summary bar showing last job status, time, and total count.
- Default to collapsed. User can expand for the full table.
- Persist expand/collapse state in localStorage.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/JobHistory.tsx` | Modify — add collapsible wrapper with summary |
| `ui/src/components/JobHistory.test.tsx` | Modify — test collapse/expand behaviour |

---

## Phase 27 — VaR Confidence Level Toggle

**Priority: Low — useful for regulatory workflows.**

**Problem:** VaR shows 95% confidence level with no way to switch to 99%. Regulatory capital requires 99%; daily P&L management typically uses 95%.

### What to build

Add a toggle or dropdown to switch between 95% and 99% confidence levels.

### Implementation

- Add a small toggle next to the confidence label: `VaR (95%) ▾`.
- On change, re-trigger the VaR calculation with the new confidence level.
- This requires the backend to support the `confidenceLevel` parameter in the calculation request (already supported via `VaRCalculationRequestDto`).

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Modify — add confidence toggle |
| `ui/src/hooks/useVaR.ts` | Modify — support confidence level parameter |
| `ui/src/components/VaRDashboard.tsx` | Modify — wire toggle to recalculation |

---

## Future Considerations (Beyond V3)

These are larger initiatives that require architectural decisions and should be planned separately:

### F1 — Live WebSocket Updates
The refresh button implies stale data. VaR, Greeks, and positions should push updates via WebSocket on a configurable interval (every 60s during market hours, every 5m overnight). The price WebSocket infrastructure already exists — extend it to risk metrics.

### F2 — Greeks Heatmap Conditional Formatting
Colour cells by magnitude (darker shade = larger exposure). The human eye processes colour patterns in milliseconds; reading numbers takes seconds. Critical when the portfolio has 10+ asset classes.

### F3 — Hedging Suggestions
The system tells you *where* you're exposed but not *what to do about it*. Proactively suggest trades that would reduce VaR, flatten delta, or bring risk back within limits. "Close 500 shares of AAPL to reduce delta by $X and VaR by $Y."

### F4 — Intraday P&L as Primary Metric
P&L should be front and centre, always visible, updating in real-time — not buried below the fold behind a "Compute P&L" button. Every trader has P&L as the first number they see in the morning.

### F5 — Keyboard Shortcuts
In a crisis, clicks are too slow. `R` to refresh, `W` for What-If, `S` for stress test, `1/2/3/4/5` for time ranges. Professional risk systems support keyboard-driven workflows.

---

## Proposed Implementation Order

| Priority | Phases | Rationale |
|---|---|---|
| **Sprint 1 — Trust** | 17, 18, 19, 20 | Fix the things that make users doubt the data |
| **Sprint 2 — Context** | 21, 22, 23, 24 | Add the missing context that turns numbers into decisions |
| **Sprint 3 — Polish** | 25, 26, 27 | Refine the experience for daily use |
| **Backlog** | F1–F5 | Larger architectural initiatives |

---

## Proposed Risk Tab Layout (After V3)

```
┌─────────────────────────────────────────────────────────┐
│ ⚠ Daily P&L exceeded $250K — current: $251,955    3h ✕ │  Ph 17
│                               Last refreshed: 23:45 ●  │
├─────────────────────────────────────────────────────────┤
│ ┌──────────┐ ┌──────────────────────┐ ┌──────────────┐ │
│ │ VaR (95%)│ │ Greeks Heatmap (USD)  │ │ Component    │ │
│ │$1,050.51 │ │                      │ │ Breakdown    │ │
│ │ ↑$32 +3% │ │ Asset    Δ    Γ    Ʋ │ │ (donut)      │ │  Ph 23
│ │ ES $1,317│ │ Equity  ...  ...  ...│ │              │ │
│ │  (1.25x) │ │ Fixed   ...  ...  ...│ │ Div: -$42K   │ │  Ph 24
│ │          │ │ ───────────────────── │ │   (17%)      │ │
│ │          │ │ Total  ΣΔ  ΣΓ  ΣV    │ └──────────────┘ │
│ └──────────┘ │ Θ: 424.40  ρ: -66.8K │                  │  Ph 20, 25
│              └──────────────────────┘                   │
│ [1h] [24h] [7d] [Today] [Custom]    [VaR/ES ◉|Greeks ○]│
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Trend Chart (auto-zoomed to data range)             │ │  Ph 21
│ └─────────────────────────────────────────────────────┘ │
│ PARAMETRIC ⓘ · 23:45:49          [What-If] [Refresh]   │
├─────────────────────────────────────────────────────────┤
│ Position Risk Table                               [▾]   │
│ Instrument | Asset Class | ... | Θ | ρ | VaR | ES%     │  Ph 19
│ ▶ AAPL (Equity)                                         │
├─────────────────────────────────────────────────────────┤
│ ┌─────────────────────┐  ┌────────────────────────────┐ │
│ │ P&L Attribution      │  │ Stress Test Summary        │ │
│ │ Total: +$12,340      │  │ Top 3 scenarios + impact   │ │
│ │ Δ/Γ/Ʋ/Θ/ρ breakdown │  │ [Run] [View Details →]     │ │
│ │ [View Full → P&L tab]│  │                            │ │
│ └─────────────────────┘  └────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│ ▸ Valuation Jobs — Last: 23:47 ✓ · 283 jobs today      │  Ph 26
│   (click to expand)                                     │
└─────────────────────────────────────────────────────────┘
```
