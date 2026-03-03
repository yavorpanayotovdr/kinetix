# Scenarios Tab Implementation Plan

**Date:** 2026-03-03
**Source:** Trader feedback (`docs/evolution/scenarios-review.md`) + team analysis (UX, QA, Data)
**Team:** ux-designer, architect, qa, data-analyst

---

## Overview

This plan covers 8 implementation steps, ordered by priority. Each step follows TDD (tests first, then implementation) and the project conventions in CLAUDE.md. Steps 1-4 are Priority 1 (Must-Have); Steps 5-8 are Priority 2 (Important).

The backend is ~70% capable already. The biggest new backend work is position-level stress impacts (small), batch endpoint (small), and limit breach checking (medium). The bulk of work is frontend.

---

## Shared Infrastructure (created in Step 1, used by all steps)

### New Frontend Types (`ui/src/types.ts` additions)

```typescript
export interface PositionStressImpactDto {
  instrumentId: string
  assetClass: string
  baseMarketValue: string
  stressedMarketValue: string
  pnlImpact: string
  percentageOfTotal: string
}

export interface StressLimitBreachDto {
  limitType: string
  limitLevel: string
  limitValue: string
  stressedValue: string
  breachSeverity: string  // "OK" | "WARNING" | "BREACHED"
  scenarioName: string
}

export interface StressScenarioDto {
  id: string
  name: string
  description: string
  shocks: string           // JSON string of { volShocks, priceShocks }
  status: string           // DRAFT | PENDING_APPROVAL | APPROVED | RETIRED
  createdBy: string
  approvedBy: string | null
  approvedAt: string | null
  createdAt: string
}

export interface CreateScenarioRequestDto {
  name: string
  description: string
  shocks: string
  createdBy: string
}

export interface ScenarioShocksDto {
  volShocks: Record<string, number>
  priceShocks: Record<string, number>
}
```

Enhanced `StressTestResultDto` (add fields):
```typescript
export interface StressTestResultDto {
  scenarioName: string
  baseVar: string
  stressedVar: string
  pnlImpact: string
  assetClassImpacts: AssetClassImpactDto[]
  positionImpacts: PositionStressImpactDto[]   // NEW
  limitBreaches: StressLimitBreachDto[]         // NEW
  calculatedAt: string
}
```

### Shared Test Mock Factory (`ui/src/test-utils/stressMocks.ts` -- new file)

```typescript
export function makeStressResult(overrides?: Partial<StressTestResultDto>): StressTestResultDto
export function makePositionImpact(overrides?: Partial<PositionStressImpactDto>): PositionStressImpactDto
export function makeLimitBreach(overrides?: Partial<StressLimitBreachDto>): StressLimitBreachDto
export const SCENARIOS_LIST: string[]
export const ALL_STRESS_RESULTS: StressTestResultDto[]
```

### Component Tree (final state)

```
ScenariosTab (rewritten)
+-- ScenarioControlBar (new)
|   +-- Button (Run All, danger)
|   +-- Select (Confidence Level)
|   +-- Select (Time Horizon)
|   +-- Button (Custom Scenario, primary)
+-- ScenarioComparisonTable (new)
|   +-- Sortable column headers
|   +-- Per-row: Badge (status), Badge (breaches), color-coded left border
+-- ScenarioDetailPanel (new, conditional on row selection)
|   +-- View toggle (Asset Class | Positions | Greeks)
|   +-- AssetClassImpactView (extracted from existing StressTestPanel bar pattern)
|   +-- StressPositionTable (new)
|   +-- LimitBreachCard (new, conditional)
+-- CustomScenarioBuilder (new, slide-over panel)
    +-- Input (name, description)
    +-- Per-asset-class: Input fields for vol/price shocks
    +-- Collapsible advanced section (confidence, horizon)
    +-- Footer (Save, Run Ad-Hoc)
```

---

## Step 1: Run All Scenarios + Comparison Table (Priority 1.1)

**Goal:** Replace the current single-scenario-at-a-time UI with a "Run All" button that displays all scenarios in a comparison table sorted by worst P&L impact.

### Dependencies
- None (first step)

### Backend Changes

**1a. Proto (`proto/src/main/proto/kinetix/risk/stress_testing.proto`)**
- Add `PositionStressImpact` message (instrument_id, asset_class, base_market_value, stressed_market_value, pnl_impact, percentage_of_total)
- Add `repeated PositionStressImpact position_impacts = 7` to `StressTestResponse`

**1b. Risk-Engine Python**
- `risk-engine/src/kinetix_risk/models.py`: Add `PositionStressImpact` dataclass and add `position_impacts: list[PositionStressImpact]` to `StressTestResult`
- `risk-engine/src/kinetix_risk/stress/engine.py`: Capture per-position base/stressed market values in the existing loop (data is already computed, just discarded). Return them on `StressTestResult`.
- `risk-engine/src/kinetix_risk/converters.py`: Add `position_stress_impact_to_proto()` converter, update `stress_result_to_proto()`.
- Tests: `risk-engine/tests/test_stress_engine.py` -- verify position_impacts returned and correct.

**1c. Risk-Orchestrator (`risk-orchestrator/`)**
- `routes/dtos/PositionStressImpactDto.kt` (new): `@Serializable data class PositionStressImpactDto`
- `routes/dtos/StressTestResponse.kt`: Add `positionImpacts: List<PositionStressImpactDto>` field
- `routes/dtos/StressTestBatchRequestBody.kt` (new): `@Serializable data class StressTestBatchRequestBody(val scenarioNames: List<String>, val calculationType: String?, val confidenceLevel: String?, val timeHorizonDays: String?)`
- `routes/RiskRoutes.kt`: Add `POST /api/v1/risk/stress/{portfolioId}/batch` handler -- fetches positions once, makes N parallel gRPC calls via `coroutineScope { scenarioNames.map { async { ... } } }`, returns array of results.
- Update existing stress route to include positionImpacts in response mapping.
- Tests: `StressTestBatchAcceptanceTest.kt` -- verify batch endpoint returns array, verify single-position fetch.

**1d. Gateway**
- `routes/StressTestRoutes.kt`: Add batch route proxy `POST /api/v1/risk/stress/{portfolioId}/batch`
- `dto/Dtos.kt`: Add `PositionStressImpactDto`, update `StressTestResponse` with `positionImpacts` field, add `StressTestBatchRequest` DTO.
- `client/RiskServiceClient.kt`: Add `runBatchStressTest(portfolioId, params)` method
- `client/HttpClientDtos.kt`: Add corresponding client DTOs
- Tests: Update `StressTestRoutesTest.kt` for batch endpoint.

### Frontend Changes

**1e. API Layer (`ui/src/api/stress.ts`)**
- Add `runAllStressTests(portfolioId: string, params?: { confidenceLevel?: string, timeHorizonDays?: string }): Promise<StressTestResultDto[]>` -- calls batch endpoint.

**1f. Hook (`ui/src/hooks/useRunAllScenarios.ts` -- new)**
- State: `results: StressTestResultDto[]`, `loading: boolean`, `error: string | null`, `selectedScenario: string | null`
- `runAll()`: Calls `runAllStressTests`, sorts by absolute pnlImpact descending.
- Clears on portfolioId change.

**1g. Components**
- `ui/src/components/ScenarioControlBar.tsx` (new): Run All button, confidence/horizon selects, custom scenario button.
- `ui/src/components/ScenarioComparisonTable.tsx` (new): Table with columns: Scenario, Status, Base VaR, Stressed VaR, VaR Multiplier (Stressed/Base as "3.0x"), P&L Impact, Limit Breaches. Sortable headers. Row click selects scenario. Selected row styled `bg-indigo-50 border-l-2 border-indigo-500`. ChevronRight/ChevronDown expand indicator.
- `ui/src/components/ScenariosTab.tsx` (rewritten): Compose ScenarioControlBar + ScenarioComparisonTable. Remove old StressTestPanel delegation.

**1h. Keep StressSummaryCard and StressTestPanel**: StressSummaryCard on the Risk tab remains but now reads from the shared batch results. StressTestPanel is kept as legacy but no longer the primary view. Add a backward compatibility test to `StressSummaryCard.test.tsx`: `"should render correctly when receiving batch results with new positionImpacts and limitBreaches fields"` to ensure the existing Risk tab summary card does not break when the DTO gains new fields.

### TDD Approach

**Tests first (write these before implementation):**

`ui/src/api/stress.test.ts` (extend):
- `"sends batch POST and returns all results"`
- `"returns partial results when some scenarios fail with 404"`
- `"includes request parameters in batch POST body"`

`ui/src/hooks/useRunAllScenarios.test.tsx` (new):
- `"should set loading to true while scenarios are running"`
- `"should return all results sorted by absolute P&L impact descending"`
- `"should set error when all scenarios fail"`
- `"should return partial results with error when some scenarios fail"`
- `"should clear results when portfolioId changes"`
- `"should not run when portfolioId is null"`

`ui/src/components/ScenarioComparisonTable.test.tsx` (new):
- `"should render column headers: Scenario, Base VaR, Stressed VaR, VaR Multiplier, P&L Impact"`
- `"should render one row per scenario result"`
- `"should display results sorted by absolute P&L impact descending"`
- `"should format VaR Multiplier as Stressed/Base ratio (e.g. 3.0x)"`
- `"should display P&L Impact with red text for losses"`
- `"should call onRunAll when Run All button is clicked"`
- `"should disable Run All button while loading"`
- `"should highlight selected scenario row on click"`
- `"should handle zero Base VaR without division by zero"`
- `"should show empty state message when no results exist"`
- `"should show error banner when run fails"`
- `"should show partial results with warning banner when some scenarios failed"`
- `"should support keyboard navigation between rows with arrow keys"`

`ui/src/components/ScenarioControlBar.test.tsx` (new):
- `"should render Run All button, confidence select, horizon select"`
- `"should call onRunAll when Run All is clicked"`
- `"should call onConfidenceLevelChange when confidence is changed"`
- `"should render Custom Scenario button"`

### Acceptance Criteria
- Clicking "Run All" fires a single batch request, not N individual requests
- Results appear in a table sorted by worst P&L impact (most negative first)
- VaR multiplier = Stressed VaR / Base VaR, displayed as ratio (e.g., "2.8x")
- Loading state shown during execution, error state on failure
- Partial results shown if some scenarios succeed and others fail
- Clicking a row selects it (visual highlight), clicking again deselects
- Confidence level and time horizon selects wired to batch request params

---

## Step 2: Position-Level Drill-Down (Priority 1.2)

**Goal:** When a scenario row is selected, show a detail panel with asset class breakdown and position-level drill-down.

### Dependencies
- Step 1 (comparison table with row selection; proto changes for position impacts)

### Backend Changes
- None beyond Step 1 (position impacts already returned by batch/single endpoints).

### Frontend Changes

**2a. Components**
- `ui/src/components/ScenarioDetailPanel.tsx` (new): Card that appears below the comparison table when a row is selected. Contains a segmented toggle (Asset Class | Positions) for switching views. Has `aria-live="polite"` for accessibility.
- `ui/src/components/AssetClassImpactView.tsx` (new): Extracted from existing StressTestPanel bar pattern. Same `ASSET_CLASS_COLORS` mapping, base/stressed bars. Asset class labels made clickable -- clicking switches to Position view filtered to that class.
- `ui/src/components/StressPositionTable.tsx` (new): Table with columns: Instrument, Asset Class, Base MV, Stressed MV, P&L Impact, % of Total Loss. Sorted by absolute P&L impact descending. Total row at bottom. Filter pill when an asset class filter is active (Badge with X to clear).
- `ui/src/components/ScenariosTab.tsx`: Wire detail panel to selected scenario.

**2b. View Toggle Pattern**: Two buttons side by side as segmented control. Active button: `bg-indigo-600 text-white`. Inactive: `bg-white text-slate-600 hover:bg-slate-50`. Same pattern as BUY/SELL in WhatIfPanel.

### TDD Approach

**Tests first:**

`ui/src/components/ScenarioDetailPanel.test.tsx` (new):
- `"should render Asset Class view by default when scenario selected"`
- `"should switch to Position view when Positions toggle is clicked"`
- `"should not render when no scenario is selected"`
- `"should have aria-live polite region for dynamic content"`

`ui/src/components/AssetClassImpactView.test.tsx` (new):
- `"should render base and stressed bars for each asset class"`
- `"should call onAssetClassClick when asset class label is clicked"`
- `"should display P&L impact per asset class"`

`ui/src/components/StressPositionTable.test.tsx` (new):
- `"should render columns: Instrument, Asset Class, Base MV, Stressed MV, P&L Impact, % of Total"`
- `"should display positions sorted by absolute P&L impact descending"`
- `"should format all monetary values as currency"`
- `"should display percentage contribution with % suffix"`
- `"should show total row at bottom summing P&L impacts"`
- `"should filter to show only positions of the selected asset class"`
- `"should show All tab and per-asset-class filter pill"`
- `"should clear filter when X is clicked on filter pill"`
- `"should show empty state when positions array is empty"`
- `"should handle positions with zero base market value"`
- `"should handle a portfolio with 100+ positions"`

### Acceptance Criteria
- Selecting a scenario row in comparison table shows the detail panel below
- Default view shows asset class impact bars (reusing existing visual pattern)
- Clicking asset class label switches to Position view filtered to that class
- Position table shows instrument, base MV, stressed MV, P&L impact, % contribution
- Sorted by absolute P&L impact descending
- Total row at bottom
- Filter pill with X dismiss button when filtered by asset class
- Deselecting the row hides the detail panel

---

## Step 3: Limit Breach Highlighting (Priority 1.3)

**Goal:** Show which risk limits would be breached under each stress scenario, with color-coded indicators.

### Dependencies
- Step 1 (comparison table)
- Step 2 (detail panel)

### Backend Changes

**3a. Risk-Orchestrator**
- `routes/dtos/StressLimitBreachDto.kt` (new): `@Serializable data class StressLimitBreachDto(limitType, limitLevel, limitValue, stressedValue, breachSeverity, scenarioName)`
- `routes/dtos/StressTestResponse.kt`: Add `limitBreaches: List<StressLimitBreachDto>` field
- `service/StressLimitCheckService.kt` (new): Takes stressed position values, fetches limit definitions from position-service (new HTTP endpoint needed), evaluates each limit. Returns `OK` (<80% utilization), `WARNING` (80-100%), `BREACHED` (>100%).
- `routes/RiskRoutes.kt`: Wire limit breach checking into stress test response (both single and batch).
- Tests: `StressLimitCheckServiceTest.kt` -- unit tests for breach evaluation logic.

**3b. Position-Service**
- `routes/PositionRoutes.kt`: Add `GET /api/v1/portfolios/{portfolioId}/limits` endpoint to expose current limit definitions and their values.
- `routes/dtos/LimitDefinitionResponse.kt` (new): DTO for limit type, level, value.
- Tests: Acceptance test for the new limits endpoint.

### Frontend Changes

**3c. Components**
- `ui/src/components/ScenarioComparisonTable.tsx`: Add "Limits" column. Render Badge per row: `variant="success"` with "OK" for no breaches, `variant="warning"` with count for amber-only, `variant="critical"` with count for any red. Add `border-l-4 border-red-500` for breached rows, `border-l-4 border-amber-400` for warning-only.
- `ui/src/components/LimitBreachCard.tsx` (new): Renders inside ScenarioDetailPanel when breaches exist. Table with columns: Limit Type, Level, Current Value, Limit Value, Utilization (progress bar), Status. Utilization bar: `h-2 rounded`, green <80%, amber 80-100%, red >100%.
- `ui/src/components/ScenarioDetailPanel.tsx`: Add LimitBreachCard section below the position/asset-class views, shown conditionally when breaches exist for the selected scenario.

### TDD Approach

**Tests first:**

`ui/src/components/LimitBreachCard.test.tsx` (new):
- `"should render green badge when utilization is below 80%"`
- `"should render amber badge when utilization is between 80% and 100%"`
- `"should render red badge when utilization exceeds 100%"`
- `"should display utilization progress bar with correct width and color"`
- `"should show 'No breaches' when all limits are within bounds"`
- `"should associate utilization bar with aria-label describing percentage"`

Extend `ScenarioComparisonTable.test.tsx`:
- `"should render Limit Breaches column header"`
- `"should show breach count per scenario row with color coding"`
- `"should color-code row left border: red for breach, amber for warning"`
- `"should show breach details when scenario row is expanded"`

### Acceptance Criteria
- Each scenario row shows a limit breach count with color-coded Badge
- GREEN: all limits <80% utilization; AMBER: any limit 80-100%; RED: any >100%
- Row left border tinted by worst breach status
- Detail panel shows specific limit names, stressed values vs limits, utilization bars
- Limit data comes from backend (not client-side calculation)

---

## Step 4: Custom Scenario Builder (Priority 1.4)

**Goal:** Slide-over panel for building custom stress scenarios with per-asset-class vol and price shocks, with ability to save (via regulatory-service) or run ad-hoc.

### Dependencies
- Step 1 (comparison table to receive ad-hoc results)
- Step 6 is an enhancement (governance badges) but not a blocker

### Backend Changes

**4a. Gateway -- Regulatory-Service Proxy Routes**
- `routes/StressScenarioRoutes.kt` (new): Proxy routes to regulatory-service:
  - `GET /api/v1/stress-scenarios` -> list all
  - `GET /api/v1/stress-scenarios/approved` -> list approved
  - `POST /api/v1/stress-scenarios` -> create
  - `PATCH /api/v1/stress-scenarios/{id}/submit` -> submit for approval
  - `PATCH /api/v1/stress-scenarios/{id}/approve` -> approve
  - `PATCH /api/v1/stress-scenarios/{id}/retire` -> retire
- `client/RegulatoryServiceClient.kt` (new): HTTP client to regulatory-service.
- Tests: `StressScenarioRoutesTest.kt`

**4b. Risk-Engine -- Scenario Details RPC**
- Add `ScenarioDetail` message to proto (name, description, vol_shocks map, price_shocks map, is_historical bool)
- Add `GetScenarioDetails` RPC or enhance `ListScenariosResponse` to include details
- `stress_server.py`: Implement handler returning data from `HISTORICAL_SCENARIOS` dict
- Tests: `test_stress_server.py`

### Frontend Changes

**4c. API Layer**
- `ui/src/api/scenarios.ts` (new): API client for regulatory-service scenario management (list, create, submit, approve, retire). Separate file from `stress.ts` per single-responsibility.
- `ui/src/api/scenarios.test.ts` (new): Tests for all scenario governance API calls.

**4d. Hook**
- `ui/src/hooks/useCustomScenario.ts` (new): Form state for scenario builder. Manages name, description, per-asset-class vol/price shocks (initialized to 1.0), confidence level, time horizon. Validation: vol 0.5-5.0, price 0.5-1.5, name required. Exposes `save()` and `runAdHoc()` actions.

**4e. Components**
- `ui/src/components/CustomScenarioBuilder.tsx` (new): Slide-over panel following WhatIfPanel pattern exactly (`fixed top-0 right-0 h-full w-[420px]`, backdrop `bg-black/30`, Escape to close). Contents:
  - Name input, description input
  - Per asset class (EQUITY, FIXED_INCOME, COMMODITY, FX, DERIVATIVE): vol shock `Input type="number"` (step=0.1, min=0.5, max=5.0) and price shock `Input type="number"` (step=0.05, min=0.5, max=1.5). Each asset class in a compact Card.
  - Collapsible "Advanced" section with confidence level Select and time horizon Input.
  - Footer: "Save & Submit" (`Button variant="primary"`) and "Run Ad-Hoc" (`Button variant="danger"` with Zap icon).
- `ui/src/components/ScenarioControlBar.tsx`: Wire "+ Custom Scenario" button to open the builder panel.
- `ui/src/components/ScenariosTab.tsx`: Manage open/close state for builder panel, wire onRunAdHoc to append result to comparison table.

### TDD Approach

**Tests first:**

`ui/src/api/scenarios.test.ts` (new):
- `"sends POST to create scenario and returns saved scenario"`
- `"throws on validation error (400)"`
- `"lists all scenarios"`
- `"lists approved scenarios"`

`ui/src/hooks/useCustomScenario.test.tsx` (new):
- `"should initialize with default shock multipliers of 1.0 for all asset classes"`
- `"should update vol shock for a specific asset class"`
- `"should validate vol shock range 0.5 to 5.0"`
- `"should validate price shock range 0.5 to 1.5"`
- `"should prevent save when name is empty"`
- `"should call save API when save is triggered"`
- `"should call run API when run ad-hoc is triggered"`
- `"should reset form when reset is called"`

`ui/src/components/CustomScenarioBuilder.test.tsx` (new):
- `"should render scenario name input"`
- `"should render vol shock input per asset class"`
- `"should render price shock input per asset class"`
- `"should render Save and Run Ad-Hoc buttons"`
- `"should show validation error when vol shock is below 0.5"`
- `"should show validation error when price shock is above 1.5"`
- `"should disable Save button when name is empty"`
- `"should call onSave with scenario data when Save is clicked"`
- `"should call onRunAdHoc with scenario data when Run Ad-Hoc is clicked"`
- `"should close on backdrop click"`
- `"should close on Escape key"`
- `"should have aria-labels on all inputs"`

### Acceptance Criteria
- "+ Custom Scenario" button opens slide-over panel (same pattern as WhatIfPanel)
- User can name the scenario and set per-asset-class vol and price shocks
- Inputs constrained to valid ranges with visible validation errors
- "Save & Submit" persists to regulatory-service (DRAFT status), panel closes
- "Run Ad-Hoc" runs immediately, appends result to comparison table, panel stays open
- Escape key and backdrop click close the panel
- Optional confidence level and time horizon in collapsible advanced section

---

## Step 5: Scenario Comparison View (Priority 2.5)

**Goal:** Side-by-side comparison of 2-3 selected scenarios.

### Dependencies
- Step 1 (comparison table)
- Step 2 (asset class impact view)

### Frontend Changes

**5a. Components**
- `ui/src/components/ScenarioComparisonTable.tsx`: Add checkbox column. When 2-3 scenarios checked, show "Compare Selected" button in control bar.
- `ui/src/components/ScenarioComparisonView.tsx` (new): Side-by-side layout. Columns per selected scenario, rows for metrics (Base VaR, Stressed VaR, P&L Impact, VaR Multiplier). Below: overlaid asset class impact bars (multiple colored bars per asset class, one per scenario).
- `ui/src/components/ScenarioControlBar.tsx`: Add "Compare Selected" button, shown when 2-3 checkboxes are checked.

### TDD Approach

`ui/src/components/ScenarioComparisonView.test.tsx` (new):
- `"should render columns for each selected scenario"`
- `"should display metrics in rows aligned across scenarios"`
- `"should overlay asset class bars for visual comparison"`
- `"should show empty state when fewer than 2 scenarios selected"`
- `"should limit to maximum 3 scenarios for comparison"`

### Acceptance Criteria
- Checkbox column in comparison table allows selecting 2-3 scenarios
- "Compare Selected" button appears when valid selection made
- Side-by-side view shows metrics aligned for easy comparison
- Asset class bars overlaid/side-by-side per scenario

---

## Step 6: Scenario Governance Integration (Priority 2.6)

**Goal:** Surface the regulatory-service's approval workflow in the UI with status badges and management section.

### Dependencies
- Step 4 (gateway proxy routes + scenarios API client already created)

### Frontend Changes

**6a. Hook**
- `ui/src/hooks/useScenarioGovernance.ts` (new): Fetches scenario list with governance metadata (status, approvedBy, etc.). Provides submit/approve/retire actions.

**6b. Components**
- `ui/src/components/ScenarioComparisonTable.tsx`: "Status" column already planned in Step 1. Wire actual governance data: Badge `variant="success"` for APPROVED, `variant="info"` for DRAFT, `variant="warning"` for PENDING_APPROVAL, `variant="neutral"` for AD-HOC/RETIRED.
- `ui/src/components/ScenarioGovernancePanel.tsx` (new): Accessible from "Manage Scenarios" link in control bar. Lists all scenarios with status, created/approved dates, action buttons (Submit for Approval, Approve). Only DRAFT scenarios show "Submit", only PENDING show "Approve". Uses ConfirmDialog for destructive actions (retire).
- `ui/src/components/ScenarioControlBar.tsx`: Add "Manage Scenarios" link/button.

### TDD Approach

`ui/src/hooks/useScenarioGovernance.test.tsx` (new):
- `"should fetch all scenarios with governance metadata"`
- `"should submit a draft scenario for approval"`
- `"should approve a pending scenario"`
- `"should retire an approved scenario"`

`ui/src/components/ScenarioGovernancePanel.test.tsx` (new):
- `"should render all scenarios with status badges"`
- `"should show Submit for Approval button on DRAFT scenarios"`
- `"should show Approve button on PENDING_APPROVAL scenarios"`
- `"should show Retire button on APPROVED scenarios"`
- `"should show confirmation dialog before retire"`
- `"should not show action buttons on RETIRED scenarios"`

### Acceptance Criteria
- Each scenario row in comparison table shows governance status Badge
- "Manage Scenarios" opens governance panel
- DRAFT scenarios can be submitted for approval
- PENDING scenarios can be approved (four-eyes: different user than creator)
- APPROVED scenarios can be retired
- Status changes reflected immediately in the UI

---

## Step 7: Export + Stressed Greeks (Priority 2.7 + 2.8)

**Goal:** CSV/PDF export of stress test results and display of stressed Greeks alongside stressed VaR.

### Dependencies
- Step 1 (comparison table with results)
- Step 2 (position drill-down data)

### Frontend Changes

**7a. Export**
- `ui/src/utils/exportStressResults.ts` (new): `exportStressResultsToCsv(results, positionDetails)` using existing `exportToCsv` pattern.
- `ui/src/components/ScenarioControlBar.tsx`: Add "Export CSV" button (secondary variant, Download icon).

**7b. Stressed Greeks**
- `ui/src/components/StressedGreeksView.tsx` (new): Table with columns: Greek, Base Value, Stressed Value, Change. Rows: Delta, Gamma, Vega, Theta, Rho. Highlights sign changes (e.g., gamma flipping). Reuses `changeColorClass` and `ChangeIcon` from WhatIfPanel.
- `ui/src/components/ScenarioDetailPanel.tsx`: Add third view toggle option: "Asset Class | Positions | Greeks".

### Backend Changes (for stressed Greeks)
- Proto: Add `repeated StressGreekValues stressed_greeks = 8` and `double stressed_theta = 9`, `double stressed_rho = 10` to `StressTestResponse`
- Risk-engine: Call `calculate_greeks()` on both base and stressed positions in `run_stress_test()`, return both.
- Risk-orchestrator: Map new proto fields to response DTO.

### TDD Approach

`ui/src/utils/exportStressResults.test.ts` (new):
- `"should produce CSV with scenario summary rows"`
- `"should include position detail rows"`
- `"should handle empty results"`

`ui/src/components/StressedGreeksView.test.tsx` (new):
- `"should render base vs stressed delta, gamma, vega per asset class"`
- `"should highlight sign changes with warning color"`
- `"should show percentage change"`
- `"should show empty state when Greeks data unavailable"`

### Acceptance Criteria
- "Export CSV" downloads file with all scenario rows + position details
- Greeks view shows base vs stressed values with change indicators
- Sign changes (e.g., gamma flipping from positive to negative) are highlighted

---

## Step 8: Scenario Descriptions and Context (Priority 2.12)

**Goal:** Each scenario shows a tooltip or expandable panel with historical context, shock parameters, last run time, and approval info.

### Dependencies
- Step 4 (scenario details RPC provides descriptions and shock data)
- Step 6 (governance data provides approval info)

### Frontend Changes

**8a. Components**
- `ui/src/components/ScenarioTooltip.tsx` (new): Rendered on hover/focus of scenario name in comparison table. Shows: scenario description, vol shocks per asset class, price shocks per asset class, last run timestamp, approval status and approver (if available). Uses a popover pattern (absolute positioned Card on hover, `z-50`).
- `ui/src/components/ScenarioComparisonTable.tsx`: Wrap scenario name cell content with ScenarioTooltip trigger.

### TDD Approach

`ui/src/components/ScenarioTooltip.test.tsx` (new):
- `"should display scenario description on hover"`
- `"should display vol and price shocks per asset class"`
- `"should display last run timestamp"`
- `"should display approval status and approver"`
- `"should hide on mouse leave"`
- `"should be accessible via keyboard focus"`

### Acceptance Criteria
- Hovering over a scenario name in the comparison table shows tooltip
- Tooltip contains: description, shock parameters, last run, governance info
- Accessible via keyboard focus (not hover-only)
- Does not block interaction with the row

---

## Playwright E2E Tests (across all steps)

New file: `ui/e2e/scenarios-tab.spec.ts`

```
test('Run All button executes all scenarios and displays comparison table')
test('clicking a scenario row expands asset class breakdown')
test('clicking an asset class bar shows position drill-down')
test('limit breach indicators appear with correct color coding')
test('custom scenario builder opens, accepts inputs, and runs ad-hoc')
test('custom scenario can be saved and appears in scenario list')
test('keyboard navigation works through comparison table rows')
test('dark mode renders all new components correctly')
```

Add mock API routes to Playwright fixtures for: `/api/v1/risk/stress/{portfolioId}/batch`, `/api/v1/stress-scenarios`, `/api/v1/stress-scenarios/*`.

---

## Implementation Order Summary

| Step | Title | Backend | Frontend | Estimated Effort |
|------|-------|---------|----------|-----------------|
| 1 | Run All + Comparison Table | Proto + Python + Orchestrator batch endpoint + Gateway | API + hook + 3 components + rewrite ScenariosTab | Large |
| 2 | Position Drill-Down | None (uses Step 1 data) | 3 new components | Medium |
| 3 | Limit Breach Highlighting | Orchestrator limit check service + Position-service limits endpoint | 1 new component + table enhancements | Medium |
| 4 | Custom Scenario Builder | Gateway proxy routes + scenario details RPC | API client + hook + 1 large component | Large |
| 5 | Scenario Comparison View | None | 1 new component + table checkbox | Small |
| 6 | Governance Integration | None (uses Step 4 proxy routes) | Hook + 1 component + badges | Medium |
| 7 | Export + Stressed Greeks | Proto + Python Greeks in stress | Utility + 1 component + export button | Medium |
| 8 | Scenario Descriptions | None (uses Step 4 details RPC) | 1 tooltip component | Small |

---

## Files Summary

### New Files
- `ui/src/test-utils/stressMocks.ts`
- `ui/src/api/scenarios.ts` + `scenarios.test.ts`
- `ui/src/hooks/useRunAllScenarios.ts` + `.test.tsx`
- `ui/src/hooks/useCustomScenario.ts` + `.test.tsx`
- `ui/src/hooks/useScenarioGovernance.ts` + `.test.tsx`
- `ui/src/components/ScenarioControlBar.tsx` + `.test.tsx`
- `ui/src/components/ScenarioComparisonTable.tsx` + `.test.tsx`
- `ui/src/components/ScenarioDetailPanel.tsx` + `.test.tsx`
- `ui/src/components/AssetClassImpactView.tsx` + `.test.tsx`
- `ui/src/components/StressPositionTable.tsx` + `.test.tsx`
- `ui/src/components/LimitBreachCard.tsx` + `.test.tsx`
- `ui/src/components/CustomScenarioBuilder.tsx` + `.test.tsx`
- `ui/src/components/ScenarioComparisonView.tsx` + `.test.tsx`
- `ui/src/components/ScenarioGovernancePanel.tsx` + `.test.tsx`
- `ui/src/components/StressedGreeksView.tsx` + `.test.tsx`
- `ui/src/components/ScenarioTooltip.tsx` + `.test.tsx`
- `ui/src/utils/exportStressResults.ts` + `.test.ts`
- `ui/e2e/scenarios-tab.spec.ts`
- `risk-orchestrator/.../dtos/PositionStressImpactDto.kt`
- `risk-orchestrator/.../dtos/StressTestBatchRequestBody.kt`
- `risk-orchestrator/.../dtos/StressLimitBreachDto.kt`
- `risk-orchestrator/.../service/StressLimitCheckService.kt`
- `gateway/.../routes/StressScenarioRoutes.kt`
- `gateway/.../client/RegulatoryServiceClient.kt`

### Modified Files
- `proto/src/main/proto/kinetix/risk/stress_testing.proto`
- `risk-engine/src/kinetix_risk/models.py`
- `risk-engine/src/kinetix_risk/stress/engine.py`
- `risk-engine/src/kinetix_risk/converters.py`
- `risk-orchestrator/.../routes/RiskRoutes.kt`
- `risk-orchestrator/.../routes/dtos/StressTestResponse.kt`
- `gateway/.../routes/StressTestRoutes.kt`
- `gateway/.../dto/Dtos.kt`
- `gateway/.../client/RiskServiceClient.kt`
- `gateway/.../client/HttpClientDtos.kt`
- `gateway/.../client/HttpRiskServiceClient.kt`
- `ui/src/types.ts`
- `ui/src/api/stress.ts`
- `ui/src/components/ScenariosTab.tsx`
- `ui/src/components/StressSummaryCard.tsx` (minor: read from batch results)
