# Risk Tab Evolution Plan — V3 (Agent Team Reviewed)

*Created: 2026-02-28*
*Follows completion of [risk-ui-v2.md](risk-ui-v2.md) (Phases 9–16 all shipped)*
*Based on live UI review by Marcus (trader advisor persona)*
*Reviewed by: Architect, UX Designer, Data Analyst, SRE, Trader, Quant*

---

## Review Summary

Six specialist agents reviewed the original V3 plan. All agreed the plan is well-structured and correctly prioritised. Key cross-cutting findings:

| Area | Finding | Source |
|---|---|---|
| **Sprint reordering** | Move Phase 23 (VaR Change) and Phase 26 (Collapse Jobs) into Sprint 1 | Trader |
| **Sprint reordering** | Move Phase 21 (Empty States) into Sprint 1 — empty states erode trust as much as formatting | UX Designer |
| **ES/VaR thresholds** | Must vary by calculation method (Parametric ~1.25 baseline vs MC/Historical with fatter tails) | Quant, Data Analyst |
| **Greek units** | These are VaR sensitivities, NOT traditional option Greeks — labeling as "($)" is incorrect | Quant |
| **Confidence toggle** | More complex than presented — must segment trend history, adjust ES/VaR thresholds, reset change indicator | Quant, SRE, Architect |
| **Staleness indicator** | Must tick in real-time via `setInterval`, not just on data refresh | SRE, UX Designer |
| **FX label edge case** | `formatAssetClassLabel("FX")` produces "Fx" — need exceptions map | Data Analyst, UX Designer, Architect |
| **Accessibility** | Info icons need keyboard support; colour-only indicators need text/icon alternatives | UX Designer |
| **Missing files** | Several phases have incomplete file lists — see per-phase details | Architect |

---

## Revised Sprint Plan

Based on team consensus, the sprints are reordered:

| Sprint | Theme | Phases | Rationale |
|---|---|---|---|
| **Sprint 1 — Trust + Layout** | Fix everything that makes users doubt the data or buries critical info | 17, 18, 19, 20, 21, 23, 26 | Trader: "VaR change is trust, not context." UX: "Empty states erode trust." Trader: "Job History pushes P&L below the fold — layout bug, not polish." |
| **Sprint 2 — Context** | Add derived metrics and consistency | 22, 24, 25 | ES/VaR ratio, PV formatting, Greek units |
| **Sprint 3 — Polish** | Structural refinements | 27 | Confidence toggle has backend dependencies |
| **Backlog** | Larger architectural initiatives | F1–F5 | Require separate planning |

---

## Design Principles

Carried forward from V1/V2:

- **Close the workflow loop.** Monitor → Investigate → Decide → Act.
- **Surface, don't duplicate.** Compact summaries with links — not rebuilt full components.
- **Earn every pixel.** Dense is fine; cluttered is not.

New for V3:

- **Numbers must be trustworthy.** Inconsistent formatting, raw enum values, and excessive precision erode trust. Every number on screen must have consistent formatting, clear units, and appropriate precision.
- **Empty states must explain themselves.** "No data available" is never acceptable. Always tell the user *why* there's no data and *what they can do about it*. Distinguish "no data yet" from "fetch error".
- **Alerts must be human-readable.** System log messages have no place on a trader-facing screen.
- **Progressive disclosure over information hiding.** *(UX Designer addition)* — Collapse secondary information with a summary, don't remove it entirely.

---

## Phase 17 — Human-Readable Alert Messages

**Priority: Critical — Sprint 1**

**Problem:** Alert messages display raw system strings like `PNL_THRESHOLD GREATER_THAN 250000.0 (current: 251954.53464219306)`.

### What to build

Transform alert messages into plain-English sentences with properly formatted numbers.

### Implementation

- Create an `alertMessageFormatter` utility that accepts the full `AlertEventDto` (not just the message string) and produces human-readable output using the structured fields (`type`, `threshold`, `currentValue`, `operator`, `severity`).
- Map alert types to templates:
  - `PNL_THRESHOLD GREATER_THAN` → "Daily P&L exceeded $250K limit — current: $251,955 (macro-hedge)"
  - `VAR_THRESHOLD GREATER_THAN` → "VaR breached $1M limit — current: $1,050,510"
  - Other types follow the same pattern.
- **Include portfolio name** in the message. *(Trader)*
- Format all numbers with appropriate precision (round to nearest dollar for values > $100K; full dollar precision for smaller values). *(Quant)*
- **Fallback:** Unknown alert types must render the raw message as-is with a console warning, never blank or crash. *(Data Analyst, UX Designer)*
- **Guard against NaN:** If `currentValue` or `threshold` are NaN/null, fall back to raw message. *(Data Analyst)*

### Accessibility *(UX Designer)*

- Add `role="alert"` to CRITICAL alerts.
- Add `aria-label` including severity level to all alerts.
- Add `aria-label="Dismiss alert"` to dismiss buttons.
- Add bold text prefix ("CRITICAL:" / "WARNING:") in the formatted message itself — colour-blind users (8% of male traders) cannot distinguish red from amber reliably.

### Files to modify

| File | Action |
|---|---|
| `ui/src/utils/alertMessageFormatter.ts` | Create — message formatting utility |
| `ui/src/utils/alertMessageFormatter.test.ts` | Create — test all alert type templates, NaN fallback, unknown types |
| `ui/src/components/RiskAlertBanner.tsx` | Modify — use formatter, add aria attributes |
| `ui/src/components/RiskAlertBanner.test.tsx` | Modify — update message text assertions *(Architect)* |

---

## Phase 18 — Hide Empty Limit Bar

**Priority: High — Sprint 1**

**Problem:** The VaR Limit utilisation bar shows "Limit 0%" when no limit is configured, implying a limit exists with zero utilisation.

### What to build

Hide the limit section entirely when no limit is configured.

### Implementation

- The `VaRGauge` already has `hasLimit = varLimit != null && varLimit > 0` — verify this guard works with live data. *(Architect: "May already work correctly — verify before writing code.")*
- Verify `useVarLimit` returns `null` (not `0` or `undefined`) when no limit exists.
- If `varLimit === 0`, treat as no limit.
- **Regulatory note** *(Quant)*: Consider showing a subtle "No limit configured" text indicator rather than hiding completely, so risk managers are reminded to set limits. Under FRTB IMA, desks must have assigned risk limits.

### Files to modify

| File | Action |
|---|---|
| `ui/src/hooks/useVarLimit.ts` | Verify — ensure null when no limit |
| `ui/src/hooks/useVarLimit.test.ts` | Add — test `threshold: 0` and missing rule edge cases *(Architect)* |
| `ui/src/components/VaRGauge.tsx` | Verify — confirm hasLimit guard |
| `ui/src/components/VaRGauge.test.tsx` | Add — test limit bar hidden when 0 or null |

---

## Phase 19 — Consistent Asset Class Label Formatting

**Priority: High — Sprint 1**

**Problem:** Component Breakdown shows "Fixed Income" (title case) but Greeks heatmap shows "FIXED_INCOME" (raw enum).

### What to build

Extract shared formatting utility with an exceptions map for abbreviations.

### Implementation

- Extract `formatAssetClassLabel` from `ComponentBreakdown.tsx` into a shared utility.
- **Add exceptions map** for known abbreviations: `FX` → "FX", `ETF` → "ETF", `CDS` → "CDS". Without this, "FX" becomes "Fx". *(Data Analyst, UX Designer, Architect)*
- Apply in `RiskSensitivities.tsx`, `PositionRiskTable.tsx`, and `ComponentBreakdown.tsx`.

### Additional consumers to check *(Architect)*

- `StressSummaryCard.tsx` — `AssetClassImpactDto` has `assetClass` field.
- `PnlSummaryCard.tsx` — may display asset class labels in position attributions.

### Files to modify

| File | Action |
|---|---|
| `ui/src/utils/formatAssetClass.ts` | Create — shared utility with exceptions map |
| `ui/src/utils/formatAssetClass.test.ts` | Create — test SCREAMING_SNAKE, abbreviations (FX, ETF) |
| `ui/src/components/ComponentBreakdown.tsx` | Modify — import shared utility |
| `ui/src/components/RiskSensitivities.tsx` | Modify — format asset class labels |
| `ui/src/components/PositionRiskTable.tsx` | Modify — format asset class labels |

---

## Phase 20 — Fix Theta/Rho Formatting and Placement

**Priority: High — Sprint 1**

**Problem:** Theta shows 4dp (`424.3970`), Rho shows different precision (`-66,840.5294`). Both float below the heatmap with no visual connection.

### What to build

Integrate theta/rho into the Greeks heatmap table (Option A) with standardised 2dp formatting.

### Implementation

- Add Theta and Rho as columns in the heatmap Total row with dashes (`—`) in per-asset-class rows. This is methodologically correct — theta and rho are portfolio-level quantities. *(Quant, Data Analyst)*
- Standardise to 2dp via `formatNum(value)` (default 2dp).
- Remove the floating `greeks-summary` div.
- **Width concern** *(Architect)*: Adding 2 columns changes the table from 4 to 6 columns. Verify layout in the `md:col-span-2` grid. If too wide, keep theta/rho below the table but with 2dp formatting and visual alignment with the table.
- **Rho context** *(Quant)*: Rho is computed as a vol-based rate proxy (`VaR_rate_bumped - VaR_base / 0.0001`), not a true interest rate curve bump. The -$66K means ~$67 P&L change per basis point. Display as "Rho: -$66,840.53" with clear units ("$/bp") in the info popover.
- **Conditional formatting** *(Trader, Data Analyst)*: Flag large rho values relative to portfolio size. Thresholds: amber if `|rho/PV| > 20%`, red if `|rho/PV| > 50%`.

### Accessibility *(UX Designer)*

- If colour-coding is added, ensure contrast meets WCAG AA (4.5:1). Current amber `#f59e0b` on white fails (2.1:1). Use darker amber `#b45309` (4.8:1) or add bold text as a secondary indicator.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/RiskSensitivities.tsx` | Modify — restructure theta/rho into table, fix precision |
| `ui/src/components/RiskSensitivities.test.tsx` | Modify — update expected formatting and structure |
| `ui/src/components/VaRDashboard.tsx` | Verify — layout still fits with wider table *(Architect)* |

---

## Phase 21 — Actionable Empty States

**Priority: High — Sprint 1** *(moved from Sprint 2 per UX Designer and Trader)*

**Problem:** "No position risk data available" and other empty states give no explanation or action.

### What to build

Replace generic empty messages with contextual explanations and recovery actions.

### Implementation

- **Position Risk Breakdown**: Distinguish two root causes *(SRE)*:
  - `data === [] && error === null` → "No position risk data — positions will appear after the next VaR calculation. [Refresh now]"
  - `data === [] && error !== null` → "Unable to load position risk — [error detail]. [Retry]"
  - The Refresh/Retry button should be a ghost button to avoid competing with the main Refresh. *(UX Designer)*
- **Greeks Trend Chart (single data point)**: Don't render a trend chart with 1 dot. Show: "Trend data requires at least 2 calculations. Current values shown in the table above." Consider requiring 3+ points before rendering the chart. *(UX Designer)*
- **VaR Trend Chart (sparse data)**: Auto-zoom to `[earliest_data - 10% padding, latest_data + 10% padding]` when data covers < 20% of the selected range. *(Data Analyst)* **Caveat** *(Architect)*: Do not override the user-selected time range — use the auto-zoom as a visual viewport adjustment within the selected range, not a range override.
- **VaR Dashboard empty** *(UX Designer)*: The `VaRDashboard.tsx:75-78` "No VaR data available" message should become: "No VaR results yet. Run a calculation to see risk metrics. [Calculate now]"
- **Apply same pattern to both chart components** *(Architect)*: VaRTrendChart and GreeksTrendChart should handle single-point case identically.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/PositionRiskTable.tsx` | Modify — contextual empty state with retry |
| `ui/src/components/PositionRiskTable.test.tsx` | Modify — test new empty state messages *(Architect)* |
| `ui/src/components/GreeksTrendChart.tsx` | Modify — handle single-point and sparse data |
| `ui/src/components/GreeksTrendChart.test.tsx` | Modify — test single-point suppression *(Architect)* |
| `ui/src/components/VaRTrendChart.tsx` | Modify — handle sparse data auto-zoom |
| `ui/src/components/VaRTrendChart.test.tsx` | Modify — test sparse data *(Architect)* |
| `ui/src/components/VaRDashboard.tsx` | Modify — improve empty state message |

---

## Phase 22 — Consistent PV Formatting

**Priority: Medium — Sprint 2**

**Problem:** PV shows as `$155.8K` (compact) in VaR gauge but `155,802.5` (raw, no `$`) in Job History.

### What to build

Use consistent formatting with currency symbols across all monetary displays.

### Implementation

- Dashboard compact views: use `formatCompactCurrency` → `$155.8K`.
- Table detail views: use `formatMoney` → `$155,802.50`.
- Both must include the `$` sign. Never show monetary values without currency symbol.
- **Expand scope** *(Architect)*: Fix VaR, ES, and PV columns in `JobHistoryTable.tsx` — all three currently use bare `toLocaleString` without `$`. Fixing only PV creates a new inconsistency.
- **VaRGauge PV** *(Architect)*: `VaRGauge.tsx:119-122` renders PV as raw `pvValue` string. Apply `formatCompactCurrency` for consistency with `RiskSensitivities.tsx:82`.
- **Edge case** *(UX Designer)*: PV of exactly `$0.00` should show as `$0` (compact) or `$0.00` (table).
- **Type inconsistency** *(Data Analyst)*: `VaRResultDto.varValue` is `string` but `ValuationJobSummaryDto.varValue` is `number | null`. Formatting utilities must handle both types.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/JobHistoryTable.tsx` | Modify — format VaR, ES, PV with currency symbols |
| `ui/src/components/JobHistoryTable.test.tsx` | Modify — update assertions *(Architect)* |
| `ui/src/components/VaRGauge.tsx` | Modify — format PV with `formatCompactCurrency` *(Architect)* |

---

## Phase 23 — VaR Change Indicator

**Priority: High — Sprint 1** *(moved from Sprint 2 per Trader: "This is the single most impactful feature in the plan.")*

**Problem:** VaR shows $1,050.51 as a static number with no indication of direction or magnitude of change.

### What to build

Show the change from prior calculation next to the headline VaR number.

### Implementation

- Compute `varChange = currentVaR - previousVaR` and `varChangePct = (varChange / previousVaR) * 100`.
- Display on a second line below the VaR value *(UX Designer)*:
  - Line 1 (large): `$1,050.51`
  - Line 2 (small, colour-coded): `↑ $32.40 (+3.2%)`
- Colour: red for VaR increase (more risk = bad), green for decrease. *(Trader: "This is one of those things 50% of systems get backwards.")*
- When VaR unchanged: show `— $0.00 (0.0%)` in neutral grey. *(UX Designer)*
- **Apples-to-apples only** *(Quant)*: Only show the delta when `calculationType` and `confidenceLevel` match between current and previous. Mixing Parametric vs Monte Carlo changes is noise, not signal.
- **SOD comparison preferred** *(Quant)*: Default to SOD baseline comparison when available, with fallback to "vs. last calc". This aligns with regulatory reporting convention.

### Edge cases

- `history.length < 2` → hide change indicator entirely (not "0%" or "N/A"). *(SRE, Data Analyst, UX Designer)*
- `previousVaR === 0` → show absolute change only, no percentage (division by zero). *(SRE, Data Analyst)*
- Stale previous value (> 8 hours old) → label "vs. prior calc at HH:MM" so trader knows the baseline. *(SRE)*
- Consider minimum 5-minute gap between "current" and "previous" to avoid showing near-zero changes from rapid refreshes. *(Data Analyst)*

### Accessibility *(UX Designer)*

- Add `aria-label="VaR increased by $32.40, 3.2 percent"` to the change indicator span.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Modify — add change indicator display |
| `ui/src/components/VaRGauge.test.tsx` | Modify — test change display, edge cases |
| `ui/src/hooks/useVaR.ts` | Modify — expose previous VaR value (same method/confidence only) |
| `ui/src/hooks/useVaR.test.ts` | Modify — test previousVaR exposure *(Architect)* |

---

## Phase 24 — ES/VaR Ratio Display

**Priority: Medium — Sprint 2**

**Problem:** ES and VaR are shown separately but their ratio (key tail risk indicator) is not displayed.

### What to build

Show the ES/VaR ratio alongside the ES value with method-aware colour coding.

### Implementation

- Compute `ratio = ES / VaR`.
- Display next to ES: `ES $1,317.38 (1.25x)`.
- **Method-dependent thresholds** *(Quant, Data Analyst)*:
  - **Parametric VaR at 95%:** Theoretical ratio is ~1.25. Anything above 1.28 is unexpected (likely computation issue). Thresholds: neutral < 1.28, amber ≥ 1.28.
  - **Historical / Monte Carlo at 95%:** Thresholds: neutral < 1.4, amber 1.4–1.8, red > 1.8.
  - **At 99% confidence** (if Phase 27 ships): Lower thresholds — neutral < 1.2, amber 1.2–1.35, red > 1.35.
- Include calculation method label: `(1.25x Parametric)`. *(Quant)*
- **Additional metric** *(Data Analyst)*: Show `ES - VaR` as "tail risk premium" in dollar terms. More actionable than ratio alone.

### Edge cases

- `VaR === 0` → hide ratio or show "N/A". *(SRE, Data Analyst)*
- `ES < VaR` (ratio < 1.0) → data quality issue. Show warning icon. *(UX Designer)*
- Both VaR and ES must be positive for ratio to be meaningful. *(Data Analyst)*

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Modify — add ratio display with method-aware thresholds |
| `ui/src/components/VaRGauge.test.tsx` | Modify — test ratio, thresholds, edge cases |

---

## Phase 25 — Greek Units / Currency Labels

**Priority: Medium — Sprint 2**

**Problem:** Greeks table shows "Delta -46.25" with no units. Magnitude discrepancies between Greeks suggest different scaling.

### What to build

Add accurate unit labels to Greek column headers, reflecting that these are VaR sensitivities.

### Implementation

**Critical finding** *(Quant)*: These Greeks are VaR sensitivities computed via bump-and-revalue, NOT traditional option-pricing Greeks. The correct units are:

| Greek | Computation | Correct Unit | Suggested Header |
|---|---|---|---|
| Delta | `(VaR_bumped - VaR_base) / 0.01` | $ per 1% price move | "Delta ($/1%)" |
| Gamma | `(VaR_up - 2*VaR_base + VaR_down) / 0.01^2` | $ per (1%)^2 | "Gamma" + popover |
| Vega | `(VaR_vol_bumped - VaR_base) / 0.01` | $ per 1pp vol | "Vega ($/1pp)" |
| Theta | `VaR(t+1) - VaR(t)` | $ per day | "Theta ($/day)" |
| Rho | `(VaR_rate_bumped - VaR_base) / 0.0001` | $ per bp | "Rho ($/bp)" |

- If headers become too wide, use abbreviated labels with mandatory info popovers.
- Add a footnote: "Sensitivities show change in VaR per unit bump. Hover headers for details."
- **Apply to both components**: `RiskSensitivities.tsx` headers AND `PositionRiskTable.tsx` COLUMNS array. *(Architect)*
- **Magnitude discrepancy** *(Data Analyst)*: Delta of -46 vs Gamma of -151K is suspicious. Verify backend Greek scaling before labelling. If gamma uses `1%^2` as bump, the large number is expected mathematically but the units must be documented.

### Model validation note *(Quant)*

A model validation team would require clear documentation distinguishing VaR sensitivities ("VaR Greeks") from traditional option-pricing Greeks. The terminology overlap is a known source of confusion.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/RiskSensitivities.tsx` | Modify — add units to headers, update popovers |
| `ui/src/components/RiskSensitivities.test.tsx` | Modify — update header assertions |
| `ui/src/components/PositionRiskTable.tsx` | Modify — add units to column headers *(Architect)* |
| `ui/src/components/PositionRiskTable.test.tsx` | Modify — update header assertions *(Architect)* |

---

## Phase 26 — Collapse Job History by Default

**Priority: High — Sprint 1** *(moved from Sprint 3 per Trader: "This is a layout bug, not a polish item.")*

**Problem:** Job History (283 jobs, 29 pages, full table) dominates below-fold area, pushing P&L and Stress cards down.

### What to build

Collapse Job History by default with a one-line summary bar.

### Implementation (Option A)

- Add collapsed summary: "Last calc: 23:47 (success) · 283 jobs today" with a status dot (green/amber/red). *(UX Designer)*
- Default to collapsed for all users. User can expand for full table.
- Persist expand/collapse state in localStorage with namespaced key `kinetix:job-history-expanded`. *(Architect)*
- **Handle edge states** *(Data Analyst, SRE)*:
  - No jobs today → "No calculations today"
  - Last job failed → "Last calc: 23:52 (FAILED)" in red, prominently even when collapsed
  - Job currently running → "Calculating... started 23:48 · 282 jobs today" with spinner *(UX Designer)*
- **Data fetching when collapsed** *(SRE)*: The collapsed summary needs current data, so the data fetch must still run even when the table is hidden. Suppress table rendering, not the fetch.
- **Failed job visibility** *(SRE)*: If a job fails while collapsed, the summary must surface it. A trader with the section collapsed must still know something went wrong.

### Regulatory note *(Quant)*

Under FRTB, the full audit trail of calculations must remain accessible. Collapsing is fine; removing is not.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/JobHistory.tsx` | Modify — add collapsible wrapper with summary bar |
| `ui/src/components/JobHistory.test.tsx` | Modify — test collapse/expand, summary states |
| `ui/src/components/RiskTab.test.tsx` | Modify — visual structure change *(Architect)* |

---

## Phase 27 — VaR Confidence Level Toggle

**Priority: Low — Sprint 3** *(all reviewers agree on low priority but flag significant complexity)*

**Problem:** VaR shows 95% confidence with no way to switch to 99% for regulatory purposes.

### What to build

Add a dropdown toggle to switch between 95% and 99% confidence levels.

### Implementation

- Add toggle next to confidence label: `VaR (95%) ▾`.
- On change, trigger recalculation with new confidence level.
- Show loading/shimmer state while recalculating. Disable toggle until result arrives. *(UX Designer)*
- Update toggle label immediately to confirm selection registered. *(UX Designer)*

### Critical dependencies and concerns

**This phase is more complex than initially scoped.** All reviewers flagged significant issues:

1. **Backend API** *(Architect)*: Verify `triggerVaRCalculation` in `api/risk.ts` accepts `confidenceLevel` parameter. May require backend change.
2. **Trend chart coherence** *(Quant, Architect, SRE)*: `VaRHistoryEntry` does NOT include `confidenceLevel`. Switching from 95% to 99% would mix data points on the same trend chart. **Must either**: (a) extend `VaRHistoryEntry` with `confidenceLevel` and filter history, or (b) clear chart on toggle, or (c) annotate the confidence level change visually.
3. **ES/VaR thresholds** *(Quant)*: Phase 24 thresholds must adjust when confidence level changes. A "green" ratio at 99% has different meaning than at 95%.
4. **VaR change indicator** *(Quant)*: Phase 23 must NOT show the ~40% VaR jump from 95%→99% toggle as a "risk increase". Reset the change indicator when confidence changes.
5. **Failure fallback** *(SRE, UX Designer)*: If 99% calc fails, revert toggle to 95% and show error, rather than displaying 99% label with stale 95% data.
6. **Rapid toggling** *(SRE)*: Debounce or disable toggle while calculation is in progress to prevent multiple simultaneous requests.
7. **Regulatory context** *(Quant)*: Label clearly: "95% (internal)" vs "99% (regulatory)". FRTB actually uses ES at 97.5%, which is yet another level not currently supported.

### Files to modify

| File | Action |
|---|---|
| `ui/src/components/VaRGauge.tsx` | Modify — add confidence toggle |
| `ui/src/hooks/useVaR.ts` | Modify — support confidence level parameter, tag history entries |
| `ui/src/components/VaRDashboard.tsx` | Modify — wire toggle to recalculation |
| `ui/src/api/risk.ts` | Verify/Modify — pass confidenceLevel in calculation request *(Architect)* |

---

## Cross-Cutting Improvements (Apply Across All Phases)

These items were identified by multiple reviewers and should be addressed alongside the phase work:

### C1 — Staleness Indicator Real-Time Ticking *(SRE, UX Designer)*

**Problem:** `LastUpdatedIndicator` computes staleness at render time but does not re-render on its own. If the backend stops responding, the indicator stays frozen at "5 min ago" indefinitely.

**Fix:** Add a `useEffect` with a 30-second `setInterval` that forces re-render, so relative timestamps and staleness colours update even without new data. Apply the same fix to `RiskAlertBanner`'s relative timestamps.

### C2 — Keyboard Accessibility for Info Popovers *(UX Designer)*

**Problem:** Info icons across `VaRGauge.tsx`, `RiskSensitivities.tsx` use `onClick` only. Not reachable via keyboard.

**Fix:** Use `<button>` elements (or add `tabIndex={0}` + `onKeyDown` with Enter/Space) for all info icon triggers. Add `aria-expanded` attribute for popover state.

### C3 — Error Recovery Actions *(UX Designer)*

**Problem:** Error states in `PositionRiskTable` and `VaRDashboard` show error messages but offer no retry action.

**Fix:** Add "Retry" buttons to all error states.

### C4 — Position Risk Hook Consistency *(SRE)*

**Problem:** When `fetchVaR` fails, `varResult` keeps last-known-good data. When `fetchPositionRisk` fails, positions are cleared to `[]`. Inconsistent.

**Fix:** Make `usePositionRisk` keep last-known-good data on error (matching `useVaR` behaviour).

### C5 — VaR Polling Overlap Guard *(SRE)*

**Problem:** The 30s poll interval in `useVaR.ts` does not check if a previous poll is still in flight. Slow responses can cause duplicate requests.

**Fix:** Add an `isPolling` ref guard to skip the interval callback if a request is already pending.

### C6 — Extract Duplicated Utilities *(Architect)*

- `computeNiceGridLines` is duplicated identically in `VaRTrendChart.tsx` and `GreeksTrendChart.tsx`. Extract to shared utility when touching these files in Phase 21.
- `formatCompactNumber` is inlined in `GreeksTrendChart.tsx` and duplicates `formatCompactCurrency.ts` minus the `$` prefix. Extract when touching in Phase 20/25.

---

## Future Considerations (Beyond V3)

### F1 — Live WebSocket Updates

**All reviewers agree this is the most architecturally significant future initiative.**

Key requirements from SRE review:
- Exponential backoff with jitter for reconnection (1s, 2s, 4s... up to 60s, ±20% jitter)
- Full state snapshot on reconnect (not incremental)
- Heartbeat-based disconnect detection (2x expected interval)
- Latest-value-wins backpressure (discard older buffered messages)
- Fallback to polling when WebSocket unavailable (corporate proxy/firewall)
- Proper cleanup on component unmount to prevent memory leaks
- `MAX_HISTORY` proportional to update interval

**Trader priority: F4 > F1 > F5 > F2 > F3**

### F2 — Greeks Heatmap Conditional Formatting

Could be pulled into V3 as a small addition to Phase 20 or 25. *(Architect)*

### F3 — Hedging Suggestions

Correctly deferred. Requires high confidence in analytics before suggesting trades. *(Trader: "I'd want high confidence before the system tells me what trades to put on.")*

### F4 — Intraday P&L as Primary Metric

**Trader: "P&L is the single most important number in trading. Full stop."** Should be treated as an urgent architectural priority, not a backlog item. The current layout has VaR first and P&L fourth — the hierarchy should be P&L → VaR → Greeks → Stress.

### F5 — Keyboard Shortcuts

Use `react-hotkeys-hook` or similar. Must not conflict with browser shortcuts. Disable when text input focused. `?` to show shortcut list. *(SRE)*

---

## Additional Metrics Suggested by Reviewers

| Metric | Description | Suggested By | Phase |
|---|---|---|---|
| **Tail Risk Premium** | `ES - VaR` in dollar terms | Data Analyst | Phase 24 addition |
| **VaR as % of PV** | `VaR / PV * 100` — contextualises risk relative to portfolio size | Data Analyst | Phase 23 addition |
| **Greeks as % of PV** | `Delta / PV * 100` — effective portfolio beta | Data Analyst | Phase 25 addition |
| **Backtesting Exception Count** | "2 exceptions in 250 days" — Basel traffic light (0-4 green, 5-9 amber, 10+ red) | Quant | Future |
| **VaR Confidence Interval Bands** | Variance of VaR estimates on trend chart (10+ data points) | Quant | Phase 21 addition |

---

## Items Identified as Missing from Original Plan

| Item | Description | Identified By |
|---|---|---|
| Position-level P&L in PositionRiskTable | Show P&L per position alongside VaR contribution | Trader |
| Position-level stress attribution | Click position to see per-instrument stress impact | Trader |
| "As-of" timestamp on VaR number | Show calculation time directly next to the VaR value | Trader |
| SOD VaR vs Current VaR comparison | Side-by-side for morning risk meetings | Trader |
| Alert sound / desktop notification | Browser notification for threshold breaches when trader is on another screen | Trader |
| Loading skeleton states | Prevent layout shift during data loading | UX Designer |
| Focus management after Refresh | Announce result to screen readers via `aria-live` region | UX Designer |
| Diversification double-negative display | Verify `-$formatMoney(benefit)` doesn't produce `--$42,000` | UX Designer |
| Market data staleness indicator | Separate from calc staleness — FRTB requires current market data inputs | Quant |

---

## Proposed Risk Tab Layout (After V3)

```
┌─────────────────────────────────────────────────────────┐
│ ⚠ WARNING: Daily P&L exceeded $250K — $251,955   3h ✕  │  Ph 17
│                               Last refreshed: 23:45 ●  │  C1
├─────────────────────────────────────────────────────────┤
│ ┌──────────┐ ┌──────────────────────┐ ┌──────────────┐ │
│ │ VaR (95%)│ │ Greeks Heatmap        │ │ Component    │ │
│ │$1,050.51 │ │                      │ │ Breakdown    │ │
│ │↑$32 +3.2%│ │ Class  Δ$/1% Γ  V$/pp│ │ (donut)      │ │  Ph 23
│ │ ES $1,317│ │ Equity  ...  ...  ...│ │              │ │
│ │  (1.25x) │ │ Fixed   ...  ...  ...│ │ Div: -$42K   │ │  Ph 24
│ │          │ │ ───────────────────── │ │   (17%)      │ │
│ │          │ │ Total  ΣΔ ΣΓ ΣV Θ  ρ │ └──────────────┘ │  Ph 20, 25
│ └──────────┘ └──────────────────────┘                   │
│ [1h] [24h] [7d] [Today] [Custom]    [VaR/ES ◉|Greeks ○]│
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Trend Chart (auto-zoomed to data range)             │ │  Ph 21
│ └─────────────────────────────────────────────────────┘ │
│ PARAMETRIC ⓘ · 23:45:49          [What-If] [Refresh]   │
├─────────────────────────────────────────────────────────┤
│ Position Risk Table                               [▾]   │
│ Instrument | Asset Class | ... | Θ | ρ | VaR | ES%     │  Ph 19
│ ▶ AAPL (Equity)                                         │  Ph 21
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
