# Cross-Book Aggregated VaR

**Status:** Draft
**Date:** 2026-03-19
**Reviewed by:** Architect, Trader, QA, UX Designer, Data Analyst

## Summary

Build true cross-book aggregated VaR with proper correlation/diversification effects. Currently the UI sums individual book VaRs when viewing multiple books, which overstates risk by ignoring inter-book diversification. The goal is to compute portfolio-level VaR across multiple books, show the diversification benefit, and decompose the aggregate back to per-book contributions via Euler allocation.

The existing `calculate_portfolio_var` function in the risk engine already handles multi-asset-class diversification correctly within a single book. Cross-book VaR extends this by feeding the engine the union of positions from multiple books and decomposing the result back to book level. No new math is required — the Euler allocation already works. What's needed is plumbing: a way to say "give me all positions for this desk, treat them as one portfolio, and decompose the result back to book level."

---

## Design Decisions

### Where does cross-book VaR computation live?

**Decision:** The risk-orchestrator handles aggregation; the risk-engine handles calculation.

The risk-engine is a pure calculation service (ADR-0021). It takes positions and market data as inputs and produces a risk number. It has no knowledge of books, portfolios, or business topology. The risk-orchestrator already owns the full VaR workflow: it fetches positions, discovers dependencies, fetches market data, and calls the engine. Cross-book VaR is this same workflow applied to the union of positions from multiple books.

No new service is needed.

### Aggregation hierarchy

**Decision:** Use the existing FIRM > DIVISION > DESK > BOOK hierarchy from `LimitLevel`.

- **Day one:** Desk-level aggregation (highest operational value for traders)
- **Phase two:** Division and firm-level roll-up
- **Later:** Custom ad-hoc groups, legal entity grouping

The `LimitHierarchyService` already walks this hierarchy for limit checks. The `desks` and `divisions` tables exist in reference-data-service. The gap is that `PositionsTable` stores `portfolioId` as a bare string with no FK to desks — the book-to-desk mapping must be fetched from reference-data-service.

### Standalone VaR sourcing

**Decision (v1):** Use per-book VaR from the existing cache (`VaRCache`) for standalone numbers rather than running N+1 engine calls.

The staleness risk is small — standalone VaRs are maintained by the scheduler. Add a `recomputeStandaloneVar: Boolean = false` flag to opt into full recomputation when consistency is critical (EOD promotion).

### Correlation matrix approach

**Decision:** Start with asset-class-level aggregation using the existing 5x5 correlation matrix.

The current engine groups positions by asset class and applies a correlation matrix across them. When positions from multiple books are combined, the same grouping produces the correct diversified VaR — the engine doesn't care about book boundaries. This avoids the complexity of book-level risk factor matrices.

**Known limitation:** If two books both hold equities but in different sectors/geographies, asset-class-level aggregation may overstate diversification. Document this and revisit when sector-level correlations are available.

### Kafka topology

**Decision:** New topic `risk.cross-book-results` rather than overloading `risk.results`.

The existing topic is keyed by single `portfolioId`. Cross-book results belong to a group, not a book. Separate topics avoid schema compatibility issues (the `schema-tests/` suite validates the existing topic schema) and prevent downstream consumers from receiving events they can't deserialise.

---

## Phase 1: Core Cross-Book VaR (Day One)

### Step 1: Proto contract

**File:** `proto/src/main/proto/kinetix/risk/risk_calculation.proto`

Add new messages and RPC:

```protobuf
message CrossBookVaRRequest {
  repeated kinetix.common.BookId book_ids = 1;
  RiskCalculationType calculation_type = 2;
  ConfidenceLevel confidence_level = 3;
  int32 time_horizon_days = 4;
  int32 num_simulations = 5;
  repeated kinetix.common.Position positions = 6;
  repeated MarketDataValue market_data = 7;
  repeated ValuationOutput requested_outputs = 8;
  int64 monte_carlo_seed = 9;
  string portfolio_group_id = 10;
}

message BookVaRContribution {
  kinetix.common.BookId book_id = 1;
  double var_contribution = 2;
  double percentage_of_total = 3;
  double standalone_var = 4;
  double diversification_benefit = 5;
}

message CrossBookVaRResponse {
  string portfolio_group_id = 1;
  repeated kinetix.common.BookId book_ids = 2;
  RiskCalculationType calculation_type = 3;
  ConfidenceLevel confidence_level = 4;
  double var_value = 5;
  double expected_shortfall = 6;
  repeated VaRComponentBreakdown component_breakdown = 7;
  repeated BookVaRContribution book_contributions = 8;
  double total_standalone_var = 9;
  double diversification_benefit = 10;
  google.protobuf.Timestamp calculated_at = 11;
  string model_version = 12;
  int64 monte_carlo_seed = 13;
}
```

Add `CalculateCrossBookVaR` RPC to `RiskCalculationService`.

**Tests first:**
- gRPC contract integration test: verify the proto includes `book_ids` repeated field and the response includes per-book contributions (risk-orchestrator integration tests)

### Step 2: Risk engine — book-level decomposition

**Files:**
- New: `risk-engine/src/kinetix_risk/cross_book_var.py`
- Modified: `risk-engine/src/kinetix_risk/server.py` (add `CalculateCrossBookVaR` servicer method)
- Modified: `risk-engine/src/kinetix_risk/models.py` (add `BookVaRContribution` dataclass)

The `calculate_portfolio_var` function already works on merged positions. The new piece is `decompose_book_contributions()` — a function that attributes asset-class-level Euler allocations back to individual books by their weighted market value shares within each asset class.

Logic:
1. Receive combined positions tagged with `book_id`
2. Call existing `calculate_portfolio_var` on the full set → get diversified VaR and asset-class component breakdown
3. For each book, compute its share of each asset class's market value
4. Allocate each asset class's component VaR to books proportional to their market value share
5. Sum per-book allocations to get each book's contribution to aggregate VaR

**Tests first (all `@pytest.mark.unit`):**
1. Two books with identical positions in the same asset class → aggregated exposure equals sum, VaR computed on combined exposure
2. Book A equity-only + Book B fixed-income-only → cross-book correlation applied, diversification benefit > 0
3. Book A long AAPL + Book B short AAPL → net exposure correctly computed (hedging case)
4. All five asset classes across multiple books → full 5x5 matrix used
5. Single book in cross-book call → result identical to existing single-book call
6. Empty book in multi-book set → excluded from calculation, not causing errors
7. All books empty → clean error, no engine call

### Step 3: Risk orchestrator — CrossBookVaRCalculationService

**New files (one type per file):**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/CrossBookVaRRequest.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/CrossBookValuationResult.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/BookVaRContribution.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/service/CrossBookVaRCalculationService.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/cache/CrossBookVaRCache.kt` (interface)
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/cache/RedisCrossBookVaRCache.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/cache/InMemoryCrossBookVaRCache.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/kafka/CrossBookRiskResultPublisher.kt` (interface)
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/kafka/KafkaCrossBookRiskResultPublisher.kt`
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/CrossBookVaRRoutes.kt`

Service phases:
1. **Fetch positions from all books in parallel** — `coroutineScope { bookIds.map { async { positionProvider.getPositions(it) } }.awaitAll() }` (existing batch pattern)
2. **Merge positions** — concatenate all position lists, tag each with originating book ID
3. **Discover dependencies and fetch market data** — feed merged list to existing `DependenciesDiscoverer` and `MarketDataFetcher` unchanged
4. **Send to risk engine** — call new `CalculateCrossBookVaR` RPC
5. **Fetch standalone VaRs** — read from `VaRCache` for each constituent book
6. **Publish and cache** — emit to `risk.cross-book-results`, store in `CrossBookVaRCache`

**Tests first (Kotest FunSpec + MockK):**
1. Fetches positions for each book, combines them, calls risk engine with full position list
2. Returns null when all books have empty positions — no engine call
3. Proceeds with available books when one returns empty, records warning
4. Records FAILED job when position fetch throws
5. Cross-book result includes portfolio VaR, per-book contributions, and diversification benefit = sum(standaloneVaRs) - portfolioVaR
6. Perfectly correlated books → diversification benefit = 0
7. Cache stores under composite key, does not overwrite single-book cache entries
8. Kafka event published with correct book set and portfolio-level VaR

### Step 4: Database schema

**New migration:** `V28__create_cross_book_valuation_jobs.sql`

```sql
CREATE TABLE cross_book_valuation_jobs (
    job_id              UUID            NOT NULL,
    group_id            VARCHAR(255)    NOT NULL,
    book_ids            JSONB           NOT NULL,
    trigger_type        VARCHAR(50)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    valuation_date      DATE            NOT NULL,
    started_at          TIMESTAMPTZ     NOT NULL,
    completed_at        TIMESTAMPTZ,
    duration_ms         BIGINT,
    calculation_type    VARCHAR(50),
    confidence_level    VARCHAR(10),
    var_value           DOUBLE PRECISION,
    expected_shortfall  DOUBLE PRECISION,
    total_standalone_var    DOUBLE PRECISION,
    diversification_benefit DOUBLE PRECISION,
    component_breakdown JSONB,
    book_contributions  JSONB,
    phases              JSONB           NOT NULL DEFAULT '[]',
    current_phase       VARCHAR(50),
    error               TEXT,
    PRIMARY KEY (job_id)
);

CREATE INDEX idx_cross_book_jobs_group_date
    ON cross_book_valuation_jobs (group_id, valuation_date DESC);
```

Note: No `CREATE INDEX CONCURRENTLY` (Flyway-incompatible). No FK to `valuation_jobs` (compressed chunks can't support referential integrity). Use `book_ids` JSONB array instead.

### Step 5: Kafka event

**New files in common module:**
- `common/src/main/kotlin/com/kinetix/common/kafka/events/CrossBookRiskResultEvent.kt`
- `common/src/main/kotlin/com/kinetix/common/kafka/events/BookVaRContributionEvent.kt`

Topic key: `portfolioGroupId` (stable partitioning per group).

**Tests:**
- Schema compatibility test in `schema-tests/`: new event schema is independent of existing `RiskResultEvent`
- Kafka integration test: published message contains full book set, distinguishable from single-book events
- Idempotency test: duplicate processing does not produce duplicate messages

### Step 6: Gateway routes

**New files (one DTO per file, not in `Dtos.kt`):**
- `gateway/src/main/kotlin/com/kinetix/gateway/dto/CrossBookVaRRequestDto.kt`
- `gateway/src/main/kotlin/com/kinetix/gateway/dto/CrossBookVaRResponseDto.kt`
- `gateway/src/main/kotlin/com/kinetix/gateway/dto/BookVaRContributionDto.kt`

**New endpoint:**
```
POST /api/v1/risk/var/cross-book
```

Request:
```json
{
  "bookIds": ["BOOK-A", "BOOK-B", "BOOK-C"],
  "portfolioGroupId": "desk-alpha",
  "calculationType": "PARAMETRIC",
  "confidenceLevel": "CL_95",
  "timeHorizonDays": 1,
  "numSimulations": 10000
}
```

Response:
```json
{
  "portfolioGroupId": "desk-alpha",
  "bookIds": ["BOOK-A", "BOOK-B", "BOOK-C"],
  "varValue": "1234567.89",
  "expectedShortfall": "1567890.12",
  "componentBreakdown": [...],
  "bookContributions": [
    {
      "bookId": "BOOK-A",
      "varContribution": "456789.01",
      "percentageOfTotal": "37.00",
      "standaloneVar": "512345.00",
      "diversificationBenefit": "55555.99"
    }
  ],
  "totalStandaloneVar": "1456789.00",
  "diversificationBenefit": "222221.11",
  "calculatedAt": "2026-03-19T10:00:00Z"
}
```

**Cached latest result:**
```
GET /api/v1/risk/var/cross-book/{portfolioGroupId}
```

**Tests:**
- Gateway contract acceptance test for the new endpoint
- Regression test: existing `GET /api/v1/risk/var/{portfolioId}` returns identical response shape after changes
- Validation: empty `bookIds` returns 400, single bookId is valid

### Step 7: UI — types and hooks

**Files:**
- `ui/src/types.ts` — add `CrossBookVaRResultDto`, `BookVaRContributionDto`
- New: `ui/src/hooks/useCrossBookVaR.ts` — parallel to `useVaR` but accepts `bookIds: string[]`

The existing `useVaR` hook is tightly bound to a single `bookId`. Rather than overloading it, create a dedicated `useCrossBookVaR` hook that:
- Accepts `bookIds: string[]` from `useHierarchySelector.effectiveBookIds`
- Calls `POST /api/v1/risk/var/cross-book` for refresh
- Calls `GET /api/v1/risk/var/cross-book/{groupId}` for cached results
- Returns `varResult`, `bookContributions`, `diversificationBenefit`, `loading`, `error`

**Tests first (Vitest):**
1. Empty `bookIds` → no API call, null result
2. Single bookId → calls cross-book endpoint with one-element list
3. `refresh` POSTs all selected book IDs
4. Partial result (some books failed) → surfaces both result and failed book list
5. Unmount cancels in-flight requests

### Step 8: UI — VaRGauge extension

**File:** `ui/src/components/VaRGauge.tsx`

In aggregated mode, show two numbers:
```
VaR (95%)  [confidence toggle]
$12,450,000    [status dot]
  Sum of books:   $15,100,000
  Benefit:        -$2,650,000   (17.5%)   [green]
```

The diversification benefit uses green text, consistent with existing `diversification-benefit` pattern in `ComponentBreakdown.tsx`.

### Step 9: UI — Book contribution table

**New component:** `ui/src/components/BookContributionTable.tsx`

A collapsible `Card` (matching `PositionRiskTable` pattern) that appears between `VaRDashboard` and `PositionRiskTable` when `aggregatedView` is true.

Columns: Book | VaR Contribution | % of Portfolio | ES Contribution | Status
- Each row clickable → navigates hierarchy selector down to that book's single-book view
- Rows use `StatusDot` with `aria-label` for accessibility
- Stale data: amber dot with tooltip when a book's last calculation is old relative to others
- CSV export via existing `exportToCsv` utility
- Scope badge: `<Badge variant="info">Division View</Badge>` or `<Badge variant="neutral">3 Books</Badge>`

### Step 10: UI — Book contribution waterfall chart

Replace the donut chart in aggregated mode with a horizontal waterfall (reuse `PnlWaterfallChart` pattern):

```
[Book A bar]                    $6.2M    41%
[Book B bar]                    $5.1M    34%
[Book C bar]                    $2.8M    19%
[Book D bar]                    $1.1M     7%
[Diversification benefit bar]  -$2.65M   [green]
[Portfolio Total bar]           $12.45M   [bold]
```

The diversification benefit bar extends left from the anchor in green. Keep the asset-class donut alongside for the portfolio-level asset class breakdown.

### Step 11: UI — VaR trend chart extension

**File:** `ui/src/components/VaRTrendChart.tsx`

In aggregated mode, add a third series: undiversified sum of book VaRs as a dashed line. The filled area between diversified VaR and undiversified sum becomes a green-tinted "benefit zone." The legend toggle supports the new series.

### Step 12: UI — State management

**Replace the amber warning banner** — remove "Showing sum of book VaRs — true aggregated VaR requires backend support." entirely once the feature is live.

**New states to handle:**
- **Partial data:** Show computed books in contribution table with values; show computing books with spinner; show failed books with red status dot and inline error
- **Stale data:** Amber dot on book rows where VaR is older than threshold relative to others
- **Empty scope:** `EmptyState` component with "No books in this division"
- **Aggregation failure with individual books available:** Show book totals with "Portfolio VaR unavailable — showing book totals only" and the undiversified sum as conservative estimate
- **Loading:** Animated pulse skeleton matching `VaRTrendChart` pattern for book contribution table rows
- **Refresh button:** Label "Recalculate All" in aggregated mode with progress count during operation

### Step 13: Playwright E2E tests

**New spec:** `ui/e2e/cross-book-var.spec.ts`

Using mock API patterns from `fixtures.ts`:

1. Selecting multiple books triggers cross-book VaR and displays portfolio-level VaR (not a sum)
2. Diversification benefit panel shows correct value with per-book breakdown expandable
3. One book with no positions → excluded with warning badge
4. Deselecting all books → empty state
5. Switching multi-book to single-book mode → single-book dashboard shown
6. Refresh button POSTs all selected book IDs (intercept and assert)
7. Cross-book result not shown in individual book tabs

### Step 14: End-to-end test

**File:** `end2end-tests/` — extend `VaRCalculationEnd2EndTest.kt`

Book trades into two separate books, call cross-book VaR endpoint, assert result is strictly less than sum of individual VaRs (assuming non-perfect correlation). This is the mathematical proof-of-concept for the feature.

---

## Phase 2: Extended Capabilities

### Marginal VaR per book
Rate of change of portfolio VaR with respect to a small increase in a book's position. For parametric VaR: `(Cov @ w)_i / port_std * z`. Critical for capital allocation decisions.

### Division and firm-level roll-up
Extend the aggregation hierarchy up from desk to division and firm level. The same `CrossBookVaRCalculationService` works — just resolve book IDs from a higher hierarchy level.

### Desk-level limit checking
Feed aggregated VaR into the existing `LimitHierarchyService` for desk-level VAR limit checks against the true diversified number rather than summed.

### CL_975 for FRTB
Wire up `CL_975` end-to-end: add to Python `ConfidenceLevel` enum, update gateway `validConfidenceLevels`, test through the stack. Required for FRTB Expected Shortfall at 97.5%.

### Desk-level backtesting
Run backtesting (Kupiec + Christoffersen) against aggregated desk VaR vs aggregated desk P&L. Requires a desk-level P&L roll-up which doesn't exist today.

### Scheduled cross-book calculations
Add `ScheduledCrossBookVaRCalculator` that iterates over configured groups. Trigger mechanism: poll `official_eod_designations` to detect when all books in a group have been promoted for a given valuation date, then compute the aggregate.

### Correlation heatmap
A new SVG grid component showing book-to-book or asset-class correlations. Diagnostic view explaining why diversification benefit is what it is.

---

## Phase 3: Future

### Incremental VaR
Change in total VaR if a specific book is removed entirely. Computationally expensive (requires re-running without that book). Can use existing what-if infrastructure for on-demand calculation.

### Custom groupings
User-defined ad-hoc groupings of books. Adds UI and data model complexity — get desk-level right first.

### Legal entity aggregation
Group by legal entity for firms with multiple entities. Requires adding legal entity to the data model.

### Multi-day historical VaR
Computed from overlapping multi-day windows rather than sqrt(T) scaling. Correct for historical simulation but more expensive.

### Stressed diversification
A "correlation spike" stress scenario (set all cross-book correlations to 0.9) showing how aggregate VaR behaves when diversification evaporates. Uses existing stress testing infrastructure.

---

## Database Schema (Full)

Beyond the `cross_book_valuation_jobs` table in Step 4, Phase 2 will need:

### Portfolio group definitions
```sql
CREATE TABLE portfolio_groups (
    group_id    VARCHAR(255) NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE portfolio_group_compositions (
    group_id        VARCHAR(255) NOT NULL REFERENCES portfolio_groups(group_id),
    portfolio_id    VARCHAR(255) NOT NULL,
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    PRIMARY KEY (group_id, portfolio_id, effective_from)
);
```

Point-in-time membership enables retrospective backtesting — reconstruct which books were in the group on any historical date.

### Aggregated VaR results (hypertable for Phase 2 trend analysis)
```sql
CREATE TABLE aggregated_var_results (
    aggregate_id            UUID            NOT NULL,
    group_id                VARCHAR(255)    NOT NULL,
    valuation_date          TIMESTAMPTZ     NOT NULL,
    constituent_job_ids     JSONB,
    aggregate_var           DOUBLE PRECISION,
    aggregate_es            DOUBLE PRECISION,
    sum_of_book_vars        DOUBLE PRECISION,
    diversification_benefit DOUBLE PRECISION,
    diversification_pct     DOUBLE PRECISION,
    calculation_type        VARCHAR(50),
    confidence_level        VARCHAR(10),
    constituent_count       INT,
    missing_book_ids        JSONB           DEFAULT '[]',
    correlation_matrix_id   BIGINT,
    calculation_basis       VARCHAR(20)     DEFAULT 'CONTEMPORANEOUS',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    promoted_at             TIMESTAMPTZ,
    promoted_by             VARCHAR(255),
    PRIMARY KEY (aggregate_id, valuation_date)
);
-- Convert to hypertable with 90-day chunks, matching valuation_jobs
```

### Book-level contributions
```sql
CREATE TABLE aggregated_var_book_contributions (
    aggregate_id    UUID            NOT NULL,
    portfolio_id    VARCHAR(255)    NOT NULL,
    book_var        DOUBLE PRECISION,
    book_es         DOUBLE PRECISION,
    component_var   DOUBLE PRECISION,
    component_var_pct DOUBLE PRECISION,
    marginal_var    DOUBLE PRECISION,
    book_weight     DOUBLE PRECISION,
    PRIMARY KEY (aggregate_id, portfolio_id)
);
```

---

## Key Risks and Mitigations

### Risk 1: Timestamp coherence across books
Different books calculated at different times use different price snapshots, producing phantom diversification or concentration.

**Mitigation:** Cross-book aggregation must only combine jobs sharing the same `valuation_date`. For EOD, only combine jobs with `run_label = 'OFFICIAL_EOD'`. Carry a `dataAsOf` field indicating the oldest constituent timestamp.

### Risk 2: Correlation matrix not positive semi-definite
Dynamic Ledoit-Wolf estimation on larger matrices can produce near-singular results.

**Mitigation:** The existing `_nearest_positive_definite` repair handles this. Add tests for 10x10+ matrices. Store condition number alongside the matrix as a quality indicator (> 1000 = warning).

### Risk 3: Cache collision between single-book and cross-book results
Cross-book result overwrites a single-book cache entry if keys collide.

**Mitigation:** Separate cache (`CrossBookVaRCache`) with composite keys. Unit test that cross-book writes don't affect single-book reads.

### Risk 4: Double-counting positions for books sharing instruments
Same instrument in two books counted twice in aggregated exposure.

**Mitigation:** This is actually correct behaviour — each book's position is a separate economic exposure. Two books each holding 100 AAPL = 200 AAPL total exposure. The engine handles this correctly via market value summation within asset class buckets.

### Risk 5: Diversification benefit narrative risk
Large diversification benefit encourages taking more gross risk, but diversification collapses in stress events when correlations spike.

**Mitigation:** Phase 3 "stressed diversification" scenario. In the interim, show a tooltip explaining that diversification benefit assumes normal market conditions.

### Risk 6: Regression to existing single-book path
New routes or cache changes inadvertently break single-book VaR.

**Mitigation:** Run existing `GatewayRiskContractAcceptanceTest` unchanged after merge. Add explicit regression test. Verify cache eviction doesn't displace single-book results.

---

## Data Quality Checks

1. **Minimum shared history:** Reject aggregation if any book has fewer than 60 trading days of history (for historical/Monte Carlo methods)
2. **Correlation matrix validation:** All diagonal = 1.0, all off-diagonal in [-1, 1], all eigenvalues >= 0
3. **Currency normalisation:** All market values normalised to base currency using FX rates from same snapshot
4. **Completeness check:** Flag missing books in `missing_book_ids` rather than silently excluding them
5. **Staleness threshold:** Warn in UI when any constituent book's VaR is older than a configurable threshold relative to others

---

## Test Priority Order

These are the tests that must exist before implementation begins (TDD):

1. **Mathematical correctness** (Python unit): Two negatively correlated books produce portfolio VaR strictly less than sum — this failing means the feature doesn't exist
2. **Cache collision** (Kotlin unit): Single-book VaR not overwritten by cross-book VaR
3. **gRPC contract** (integration): Proto request includes `book_ids` repeated field, response includes per-book contributions
4. **Kafka schema compatibility** (schema test): New event independent of existing `RiskResultEvent`
5. **Partial failure policy** (Kotlin unit): System behaviour when one book is unavailable — encoded in test before implementation
6. **Playwright proof** (E2E): Diversification benefit visible in UI with correct value

---

## Files Most Affected

| Layer | Key Files |
|-------|-----------|
| Proto | `proto/src/main/proto/kinetix/risk/risk_calculation.proto` |
| Risk engine | `risk-engine/src/kinetix_risk/cross_book_var.py` (new), `server.py`, `models.py` |
| Orchestrator | `CrossBookVaRCalculationService.kt` (new), `CrossBookVaRRoutes.kt` (new), cache + kafka (new) |
| Common | `CrossBookRiskResultEvent.kt` (new), `BookVaRContributionEvent.kt` (new) |
| Gateway | `CrossBookVaRRequestDto.kt` (new), `CrossBookVaRResponseDto.kt` (new), `BookVaRContributionDto.kt` (new) |
| Database | `V28__create_cross_book_valuation_jobs.sql` (new) |
| UI types | `ui/src/types.ts` |
| UI hooks | `ui/src/hooks/useCrossBookVaR.ts` (new) |
| UI components | `VaRGauge.tsx`, `VaRDashboard.tsx`, `RiskTab.tsx`, `VaRTrendChart.tsx`, `ComponentBreakdown.tsx`, `BookContributionTable.tsx` (new) |
| UI E2E | `ui/e2e/cross-book-var.spec.ts` (new) |
| Kafka | New topic `risk.cross-book-results` |
