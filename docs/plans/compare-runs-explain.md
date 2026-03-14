# Compare Runs: Input Changes Explain

**Status:** Complete
**Date:** 2026-03-14
**Reviewed by:** Architect, Trader, Tech-Support, QA, Data Analyst, Quant, SRE, UX Designer

## Summary

Add an "Input Changes" breakdown to the Compare Runs feature. When two risk calculation runs are compared, in addition to showing output diffs (VaR, Greeks, ES changes), the system will show what changed in the underlying inputs and estimate which changes are driving the result differences.

The feature is framed as a **diagnostic guide**, not exact attribution. Magnitude indicators (Large / Medium / Small) replace precise percentages. This prevents traders from trying to reconcile components to 100% — which is impossible due to non-linearity.

---

## Prerequisites (P0 — Must Fix First)

### 1. Wire up RunManifestCapture in Application.kt

**Severity:** Critical — the entire feature is a no-op without this.

`runManifestCapture` is `null` in `moduleWithRoutes()` (line ~250 of `Application.kt`). `VaRCalculationService` receives a nullable `runManifestCapture` parameter that defaults to `null`, so `NoOpRunManifestCapture` is used. The tables exist, the code exists, the data is never written.

**Fix:** Pass the real `DefaultRunManifestCapture` instance to `VaRCalculationService` in `moduleWithRoutes()`.

**Files:** `risk-orchestrator/src/main/kotlin/com/kinetix/risk/Application.kt`

### 2. Fix modelVersion null in RunSnapshotMapper

`ValuationJob.toRunSnapshot()` hardcodes `modelVersion = null` because `ValuationJob` does not carry that field — it lives in `RunManifest`. Day-over-day and job-by-job comparisons show null model version; only MODEL comparison mode shows it correctly.

**Fix:** Look up the manifest via `manifestId` on the job and populate `modelVersion` from it.

**Files:** `risk-orchestrator/src/main/kotlin/com/kinetix/risk/mapper/RunSnapshotMapper.kt`

### 3. Fix misleading zeros in VaRAttributionPanel

`VaRAttributionService` hardcodes `volEffect`, `corrEffect`, and `modelEffect` to `0.0`. The UI renders "Volatility Effect: 0.00" — a trader after a big vol move sees zero vol effect and a large unexplained, and cannot tell if this is real or a stub.

**Fix:** Either label those rows "Not yet computed" with a tooltip, or remove them until the backend actually computes them.

**Files:**
- `risk-orchestrator/src/main/kotlin/com/kinetix/risk/service/VaRAttributionService.kt`
- `ui/src/components/VaRAttributionPanel.tsx`

---

## Architecture

### Two-Tier Diff Model

**Tier 1 — Fast path (always included in comparison response):**
A SQL JOIN between two manifests' data. No payload deserialization. Produces:
- Binary `positionDigest` match indicator (O(1) string equality)
- Binary `marketDataDigest` match indicator (O(1) string equality)
- Per-instrument position deltas (quantity, market price) from `run_position_snapshots`
- Per-(dataType, instrumentId) hash change list from `run_manifest_market_data`

**Tier 2 — Quantitative diff (lazy, user-triggered per item):**
Fetch two blobs from `run_market_data_blobs` for a changed `(dataType, instrumentId)` pair, deserialize, compute magnitude. Separate endpoint triggered by UI expand action.

### Digest Short-Circuit

If both `positionDigest` and `marketDataDigest` match, zero snapshot rows are loaded. This is the common intraday re-run case where only a parameter changed.

### Data Flow

```
compareByJobIds()
  ├── Load ValuationJob (existing)
  ├── Load RunManifest via job.manifestId
  ├── Compare positionDigest / marketDataDigest (O(1))
  ├── If positionDigest differs:
  │     Load RunPositionSnapshots for both manifests
  │     Compute PositionInputChange list
  ├── If marketDataDigest differs:
  │     Load RunManifestMarketData for both manifests (hash-level JOIN)
  │     Identify changed (dataType, instrumentId) pairs
  └── Return InputChangeSummary alongside existing RunComparison

Lazy quant diff (separate endpoint):
  ├── Receive (dataType, instrumentId, baseManifestId, targetManifestId)
  ├── Look up contentHash for each manifest
  ├── Fetch both blobs from MarketDataBlobStore
  ├── Deserialize and compute magnitude
  └── Return MarketDataQuantDiffResponse
```

---

## Database Changes

### New Migration: V23__add_input_changes_indexes.sql

```sql
-- Composite index for cross-manifest market data hash comparison
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rmmd_data_type_instrument_hash
    ON run_manifest_market_data (data_type, instrument_id, content_hash);

-- Index for blob store lookup by data type and instrument (support diagnostics)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rmdb_data_type_instrument
    ON run_market_data_blobs (data_type, instrument_id);

-- Index for reverse lookup from manifest to job
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_valuation_jobs_manifest_id
    ON valuation_jobs (manifest_id)
    WHERE manifest_id IS NOT NULL;

-- Index for blob retention cleanup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rmdb_created_at
    ON run_market_data_blobs (created_at DESC);
```

**Important:** This migration must use `CREATE INDEX CONCURRENTLY` and cannot run inside a transaction. Flyway configuration must set `mixed = true` or use a non-transactional migration annotation.

### Retention Policy for run_market_data_blobs

Currently no retention policy exists. The table grows unboundedly. A scheduled cleanup job is needed:

```sql
DELETE FROM run_market_data_blobs
WHERE content_hash NOT IN (
    SELECT content_hash FROM run_manifest_market_data
    WHERE manifest_id IN (
        SELECT manifest_id FROM run_manifests
        WHERE captured_at > NOW() - INTERVAL '90 days'
    )
)
AND created_at < NOW() - INTERVAL '90 days';
```

---

## Backend Implementation

### New Domain Models

Location: `risk-orchestrator/src/main/kotlin/com/kinetix/risk/model/`

| File | Type | Purpose |
|---|---|---|
| `InputChangeSummary.kt` | data class | Fast-path result: flags + position/market data change lists |
| `PositionInputChange.kt` | data class | Per-instrument position delta: quantity, price, currency |
| `PositionInputChangeType.kt` | enum | ADDED, REMOVED, QUANTITY_CHANGED, PRICE_CHANGED, BOTH_CHANGED |
| `MarketDataInputChange.kt` | data class | Per-(dataType, instrumentId) hash mismatch + lazy magnitude |
| `ChangeMagnitude.kt` | enum | LARGE, MEDIUM, SMALL |

`InputChangeSummary`:
```kotlin
data class InputChangeSummary(
    val positionsChanged: Boolean,
    val marketDataChanged: Boolean,
    val modelVersionChanged: Boolean,
    val baseModelVersion: String,
    val targetModelVersion: String,
    val positionChanges: List<PositionInputChange>,
    val marketDataChanges: List<MarketDataInputChange>,
)
```

Add `inputChanges: InputChangeSummary?` to `RunComparison` (nullable — null when manifests don't exist).

### New Services

Location: `risk-orchestrator/src/main/kotlin/com/kinetix/risk/service/`

**InputChangeDiffer.kt** — Pure function class, no I/O, independently testable:
```kotlin
class InputChangeDiffer {
    fun computeInputChanges(
        baseManifest: RunManifest,
        targetManifest: RunManifest,
        basePositions: List<PositionSnapshotEntry>,
        targetPositions: List<PositionSnapshotEntry>,
        baseMarketDataRefs: List<MarketDataRef>,
        targetMarketDataRefs: List<MarketDataRef>,
    ): InputChangeSummary
}
```

**MarketDataQuantDiffer.kt** — Lazy quantitative diff from two blob payloads:
```kotlin
class MarketDataQuantDiffer {
    fun computeMagnitude(dataType: String, basePayload: String, targetPayload: String): ChangeMagnitude
}
```

### Magnitude Thresholds

| Data Type | LARGE | MEDIUM | SMALL |
|---|---|---|---|
| SPOT_PRICE, RISK_FREE_RATE, DIVIDEND_YIELD, CREDIT_SPREAD | \|delta/base\| > 5% | > 1% | <= 1% |
| YIELD_CURVE, FORWARD_CURVE | Mean abs point change > 5% | > 1% | <= 1% |
| VOLATILITY_SURFACE | ATM vol shift at nearest maturity > 5% | > 1% | <= 1% |
| CORRELATION_MATRIX | Mean abs off-diagonal change > 0.10 | > 0.03 | <= 0.03 |

These are diagnostic thresholds, easily configurable as constants in `MarketDataQuantDiffer`.

### Wire into RunComparisonService

Add `manifestRepo: RunManifestRepository?` and `inputChangeDiffer: InputChangeDiffer?` as nullable constructor parameters (defaults to null so existing tests compile without change).

In `compareByJobIds`, after loading both jobs:
```kotlin
private suspend fun loadInputChanges(baseJobId: UUID, targetJobId: UUID): InputChangeSummary? {
    val repo = manifestRepo ?: return null
    val differ = inputChangeDiffer ?: return null
    val baseManifest = repo.findByJobId(baseJobId) ?: return null
    val targetManifest = repo.findByJobId(targetJobId) ?: return null
    // Digest short-circuit: skip row loading when digests match
    val basePositions = if (baseManifest.positionDigest != targetManifest.positionDigest)
        repo.findPositionSnapshot(baseManifest.manifestId) else emptyList()
    // ... same pattern for market data refs
    return differ.computeInputChanges(...)
}
```

### New Endpoint (Lazy Quant Diff)

Location: `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RunComparisonRoutes.kt`

```
GET /api/v1/risk/compare/{portfolioId}/input-changes/{baseJobId}/{targetJobId}/market-data-quant
    ?dataType=SPOT_PRICE&instrumentId=AAPL
```

Returns:
```kotlin
@Serializable
data class MarketDataQuantDiffResponse(
    val dataType: String,
    val instrumentId: String,
    val magnitude: String,         // "LARGE" | "MEDIUM" | "SMALL"
    val diagnostic: Boolean = true, // always true — signals estimate to UI
)
```

### New Response DTOs

Location: `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/`

One file per DTO: `InputChangesSummaryDto.kt`, `PositionInputChangeDto.kt`, `MarketDataInputChangeDto.kt`, `MarketDataQuantDiffResponse.kt`.

Add `inputChanges: InputChangesSummaryDto?` to `RunComparisonResponse`.

### Gateway Proxy Route

Location: `gateway/src/main/kotlin/com/kinetix/gateway/routes/RunComparisonRoutes.kt`

Add proxy route for the quant diff endpoint. The `RiskServiceClient` interface needs one new method.

---

## Attribution Methodology

### Approach: First-Order Sensitivity (Diagnostic, Not Attribution)

Full sequential substitution is mathematically ideal but requires re-running the engine with overridden inputs — too expensive for an on-demand diagnostic. Instead, use the already-stored VaR-Greeks:

```
attributed_delta_VaR[price, ac]  = delta_A[ac]  * relative_spot_change[ac]
attributed_delta_VaR[vol, ac]    = vega_A[ac]   * vol_change_pp[ac]
```

For correlation (parametric VaR only):
```
corr_contribution = sum_{i<j} (z * sqrt_T * dollar_vol_i * dollar_vol_j / port_std) * delta_rho_ij
```

The residual `delta_V - sum(attributed terms)` is shown as "Other / Unexplained" — never hidden.

### Magnitude Classification

Use **relative-to-total-VaR-change** with an absolute floor:

```
absolute_share = |c_i| / max(|dV|, VaR_floor)
where VaR_floor = 0.5% * total_portfolio_value

LARGE:  absolute_share > 0.40
MEDIUM: absolute_share > 0.15
SMALL:  absolute_share <= 0.15
```

The `VaR_floor` prevents denominator collapse when total VaR barely changes.

### Market Data Summarization

| Input Type | Summary Metric |
|---|---|
| Vol surface | ATM vol at 30d tenor: `vol_surface.vol_at(spot, 30) delta` |
| Yield curve | Parallel shift: `mean(r_B - r_A)` across standard tenors; Slope: 10s2s spread change |
| Correlation matrix | Mean absolute off-diagonal change + the specific pair with largest change |
| Historical prices | Annualized vol change from the time series |

### When First-Order Breaks Down

Flag these scenarios explicitly in the UI:

1. **Large gamma positions** — if `|gamma * (delta_spot)^2| > 0.1 * |delta * delta_spot|`, show "may understate second-order effects"
2. **Vol smile dynamics** — ATM vol unchanged but skew changed: vega attribution not interpretable
3. **Historical VaR** — not a differentiable function of inputs; switch to scenario-description mode
4. **Different Monte Carlo seeds** — flag as "simulation sampling variance may contribute to difference"
5. **Correlation regime change** — if spectral norm of C_B - C_A > 0.15, label "attribution approximate"

---

## UI Design

### Information Architecture

Insert Input Changes **between** snapshot cards and output diffs:

```
1. EOD auto-select notice (conditional)
2. Export CSV
3. Base / Target snapshot cards
4. ** Input Changes ** (new — collapsed by default, summary headline visible)
5. Run Diff Summary (output changes)
6. Component Diff Chart
7. Position Diff Table
8. VaR Attribution Panel
```

The existing Parameter Differences block (currently unstyled, orphaned at line 95-113 of `GenericRunComparisonPanel`) is absorbed into the Input Changes section and removed from its current location.

### Component Structure

```
InputChangesPanel                         (Card wrapper)
  InputChangesSummaryBar                  (always-visible single-line diagnostic)
  InputChangesBody                        (conditionally rendered when expanded)
    PositionInputSection                  (new / removed / modified positions)
    MarketDataInputSection                (spot price moves, ATM vol shifts, yield curve)
    ModelParameterSection                 (model version, sim count)
```

### Progressive Disclosure

**Level 1 — Summary bar (always visible, 1 line):**
Diagnostic sentence: "3 position changes, spot prices moved up to Large, model version unchanged"
Count badge visible in collapsed state. Chevron toggle.

**Level 2 — Expanded body (three independently collapsible sub-sections):**
- **Position changes:** compact table with quantity delta, notional delta, ChangeBadge. Top 10 by absolute notional delta.
- **Market data:** stacked list of top N price moves with MagnitudeIndicator. Vol surface shifts and yield curve shifts below.
- **Model / Parameters:** model version before/after diff, simulation count with magnitude indicator.

**Level 3 — Full data:**
"View all N changes" link below each sub-section, expanding to full list.

### Magnitude Indicator Visual Design

```
LARGE  — filled circle, amber-700 text, amber-100 bg   dark: amber-400/amber-900/30
MEDIUM — diamond icon, blue-700 text, blue-100 bg       dark: blue-400/blue-900/30
SMALL  — empty circle, slate-500 text, slate-100 bg     dark: slate-400/surface-700 bg
```

Amber (not red) for Large — because a large input change is not inherently bad (red implies loss/negative). The text label ("LARGE", "MED", "SMALL") is always visible alongside the icon for accessibility.

### Empty and Edge States

| State | Display |
|---|---|
| Inputs identical | Panel visible, collapsed, summary: "Inputs identical" with green CheckCircle icon |
| Input data unavailable | Panel visible, body: "Input change data not available for this comparison type" |
| Partial data | Available sub-sections render normally; unavailable show placeholder |
| Loading (separate fetch) | Spinner with `aria-live="polite"` inside panel body |
| Very large change lists | Top 10 shown, "Show all N changes" expansion link |

### Accessibility

- Expand/collapse uses `<button>` with `aria-expanded` and `aria-controls`
- Sub-section headings use `<h4>` for screen reader heading hierarchy
- Magnitude icons are `aria-hidden="true"`; text label is the accessible name
- Keyboard: toggle receives focus in natural tab order, standard DOM flow inside

---

## Infrastructure and Reliability

### Memory Protection (P1 — OOMKill Risk)

The JVM is capped at 1Gi. A single diff request for a large portfolio can load 10-20 MB of blob payloads. With no back-pressure, concurrent diffs can OOMKill the pod.

**Mitigations:**
1. Size guard on blob fetch — refuse if blob > 1 MB, return 422
2. Increase memory limit from 1Gi to 2Gi in `values-prod.yaml`
3. Process changed blobs one at a time (lazy-load design), release references before next
4. Wrap blob deserialization in `withTimeout(30_000)` — return 503 on timeout

### Connection Pool Protection (P1)

Single HikariCP pool of 8 connections shared between risk calculation and diff queries. Diff queries can exhaust the pool and starve calculations.

**Mitigations:**
1. Add a concurrency semaphore on the diff endpoint (max 2 simultaneous blob deserializations)
2. Consider a separate read-only datasource for analytics queries

### Rate Limiting (P1)

No rate limiting exists on any risk-orchestrator endpoint.

**Three layers:**
1. **Per-user rate limit** at the gateway: 10 diff requests per user per minute
2. **Concurrency semaphore** at risk-orchestrator: max 2 active diff computations, 429 on overflow
3. **Complexity limit**: if changed market data items > 50, return 422 suggesting async job

### Caching Strategy

Do **not** cache the full diff result. Instead:
- Hash-level comparison: computed on demand every time (cheap SQL JOIN)
- Quantitative diff: cache per `(content_hash_A, content_hash_B)` pair in Redis with 24hr TTL
- Blobs are immutable (content-addressed) so no invalidation needed

### Monitoring

**Metrics to add:**
```
manifest_diff_requests_total          (counter, labels: portfolioId, comparison_type)
manifest_diff_duration_seconds        (histogram, buckets: 0.1, 0.5, 1, 5, 10)
manifest_blob_fetch_duration_seconds  (histogram)
manifest_blob_size_bytes              (histogram)
manifest_diff_cache_hits_total        (counter)
```

**Alerts:**
```yaml
- alert: ManifestDiffSlow
  expr: histogram_quantile(0.95, rate(manifest_diff_duration_seconds_bucket[5m])) > 10
  for: 3m
  severity: warning

- alert: ManifestBlobLarge
  expr: histogram_quantile(0.99, rate(manifest_blob_size_bytes_bucket[15m])) > 1048576
  for: 5m
  severity: warning
```

---

## Edge Cases

### Data Integrity

| Edge Case | Handling |
|---|---|
| Job has no manifest (pre-P0-fix runs) | Return `inputChanges: null`, UI shows "data not available" |
| Manifest exists but position snapshot is empty | Detect via `positionCount` mismatch, surface warning |
| PARTIAL manifest (some fetches failed) | Surface MISSING items as "unavailable in run X", not as "changed" |
| Empty-string contentHash (FetchFailure sentinel) | Classify as `UNCHANGED_MISSING`, not `UNCHANGED_FETCHED` |
| Orphaned blob (hash in manifest, blob deleted) | Return structured "data unavailable" error, not 500 |
| Different portfolios in comparison | Validate both jobs share same portfolioId, return 400 if not |
| Weekend/holiday target date | Return 404 with message, not 500 |
| Position quantity = 0 in base | Guard against divide-by-zero in percentage calculation |

### Model-Specific

| Edge Case | Handling |
|---|---|
| Different Monte Carlo seeds | Flag: "simulation sampling variance may contribute" |
| Historical VaR with different return windows | Switch to scenario-description mode, not sensitivity attribution |
| Static vs dynamic correlation | Note whether correlation was static (no change possible) or estimated |
| Model version changed between runs | Prominent banner, not buried in parameter list |

---

## Test Strategy

### Unit Tests: InputChangeDiffer (29 tests)

**Manifest-level hash comparison:**
1. positionDigest identical → positionsChanged = false, no diffs loaded
2. marketDataDigest identical → marketDataChanged = false
3. Both digests identical → totalChangedInputs = 0
4. Both digests differ → both sections marked changed

**Position input diffs:**
5. Position in base only → REMOVED with quantityDelta = -baseQuantity
6. Position in target only → ADDED with quantityDelta = +targetQuantity
7. Only quantity changed → MODIFIED with correct quantityDelta
8. Only marketPrice changed → MODIFIED with correct priceDelta
9. Both identical → UNCHANGED
10. Magnitude classification at threshold boundaries
11. Sort by magnitude descending
12. Base quantity = 0 → no divide-by-zero

**Market data input diffs (hash-level):**
13. Same contentHash → UNCHANGED
14. Ref in base only → REMOVED_IN_TARGET
15. Ref in target only → ADDED_IN_TARGET
16. Different contentHash → CHANGED
17. MISSING → FETCHED transition
18. FETCHED → MISSING transition
19. Empty-string contentHash sentinel → MISSING, not identical

**Quantitative diffs by data type:**
20. ScalarMarketData: priceDelta and priceDeltaPercent, including base = 0
21. CurveMarketData: ATM shift and tenor-by-tenor differences
22. MatrixMarketData: maxAbsChange across cells
23. TimeSeriesMarketData: most recent point delta and vol difference

**Edge cases:**
24. Empty manifests → totalChangedInputs = 0, no NPE
25. All-changed → count equals ref count, overall LARGE
26. Same-hash → empty changes list (not null)
27. Asymmetric instruments across runs
28. Mismatched params with identical data digests
29. Boundary conditions on magnitude thresholds

### Unit Tests: MarketDataQuantDiffer (6 tests)

1. LARGE spot price move (> 5%)
2. MEDIUM spot price move (1-5%)
3. SMALL spot price move (< 1%)
4. LARGE correlation regime change (mean off-diagonal > 0.10)
5. ATM vol shift from vol surface blobs
6. Parallel yield curve shift classification

### Acceptance Tests: API Endpoints (11 tests)

1. 200 with InputChangesResponse when both manifests exist and differ
2. 404 when base job has no manifest
3. 404 when target job has no manifest
4. 400 for invalid UUID
5. 400 for missing required query param
6. 200 with empty changes when both digests match
7. 200 with positionsChanged=true, marketDataChanged=false when only positions differ
8. 200 with warning when manifest is PARTIAL
9. Lazy quant diff: 200 with correct magnitude for known changed hash pair
10. Lazy quant diff: 404 when blob not found
11. Lazy quant diff: 400 for unrecognized dataType

### Playwright Browser Tests (18 tests)

**Section rendering:**
1. Input Changes section visible in comparison panel
2. "Inputs identical" message when no changes
3. Count badge shows number of changes
4. Collapsed by default, expandable on click

**Magnitude indicators:**
5-7. LARGE/MEDIUM/SMALL indicators render with correct accessible labels

**Lazy-load:**
8. Quant diff API not called before row expand
9. Quant diff API called on row expand
10. Delta value rendered on successful load
11. Error message on 404

**Position changes:**
12-14. ADDED/REMOVED/MODIFIED badges, quantity delta, price delta

**Edge cases:**
15. PARTIAL manifest warning banner
16. Error state on 500
17. Dark mode rendering
18. Diagnostic disclaimer visible

### Performance Tests (5 scenarios)

1. 500 instruments, 5 deps each: response < 200ms at p95
2. 2000 positions, full market data: hash-level comparison < 500ms
3. TimeSeriesMarketData blob (504 observations): deserialize < 50ms
4. 10 concurrent diff requests: no connection pool exhaustion
5. 7.3M rows after 1 year: query scales with per-manifest deps, not total table size

### Existing Tests to Update

- `RunComparisonAcceptanceTest`: assert `inputChanges` field shape (present or null)
- `RunComparisonServiceTest`: add MockK stubs for `manifestRepo.findByJobId()`
- `run-comparison.spec.ts`: extend `mockComparisonRoutes()` with optional `inputChanges` param
- `fixtures.ts`: add `mockInputChangesRoute()` helper

---

## Implementation Phases

### Phase 1: Foundation (Prerequisites + Fast Path)

1. **Wire RunManifestCapture** in Application.kt (P0)
2. **Fix modelVersion null** in RunSnapshotMapper (P0)
3. **Fix VaR attribution zeros** — label as "Not computed" (P0)
4. **Database migration** V23 — add indexes (use CONCURRENTLY)
5. **InputChangeDiffer** with unit tests (TDD)
6. **Wire into RunComparisonService** — add `inputChanges` to response
7. **Add cross-portfolio validation** in compareByJobIds
8. **UI: InputChangesPanel** — renders position changes and hash-level market data changes
9. **Playwright tests** for the new panel

### Phase 2: Quantitative Diffs

1. **MarketDataQuantDiffer** with unit tests (TDD)
2. **Lazy quant diff endpoint** on RunComparisonRoutes
3. **Gateway proxy route**
4. **Memory protection** — blob size guard, timeout, semaphore
5. **UI: lazy-load on expand** — fetch quant diff per row, show magnitude
6. **Redis caching** for `(hash_A, hash_B)` pair results
7. **Rate limiting** at gateway and risk-orchestrator levels
8. **Monitoring metrics and alerts**
9. Acceptance tests and Playwright tests for quant diff

### Phase 3: Attribution Enrichment

1. **Market data summarization** — ATM vol extraction, yield curve parallel shift, correlation summary
2. **First-order VaR change attribution** using stored Greeks
3. **Magnitude classification** using relative-to-total-change thresholds
4. **Diagnostic caveats** for historical VaR, different seeds, large gamma, vol smile
5. **Blob retention policy** — scheduled cleanup job
6. Performance tests at scale

---

## Files Summary

### New Files

| File | Purpose |
|---|---|
| `risk-orchestrator/.../model/InputChangeSummary.kt` | Domain model |
| `risk-orchestrator/.../model/PositionInputChange.kt` | Domain model |
| `risk-orchestrator/.../model/PositionInputChangeType.kt` | Enum |
| `risk-orchestrator/.../model/MarketDataInputChange.kt` | Domain model |
| `risk-orchestrator/.../model/ChangeMagnitude.kt` | Enum |
| `risk-orchestrator/.../service/InputChangeDiffer.kt` | Fast-path diff logic |
| `risk-orchestrator/.../service/MarketDataQuantDiffer.kt` | Lazy quant diff |
| `risk-orchestrator/.../routes/dtos/InputChangesSummaryDto.kt` | Response DTO |
| `risk-orchestrator/.../routes/dtos/PositionInputChangeDto.kt` | Response DTO |
| `risk-orchestrator/.../routes/dtos/MarketDataInputChangeDto.kt` | Response DTO |
| `risk-orchestrator/.../routes/dtos/MarketDataQuantDiffResponse.kt` | Quant diff response |
| `risk-orchestrator/.../resources/db/risk/V23__add_input_changes_indexes.sql` | DB indexes |
| `ui/src/components/InputChangesPanel.tsx` | UI panel |
| `ui/src/components/InputChangesPanel.test.tsx` | Vitest unit tests |
| `ui/src/components/MagnitudeIndicator.tsx` | Visual indicator component |
| `ui/src/utils/inputChangeMagnitude.ts` | Threshold classification utility |
| `ui/e2e/run-comparison-inputs.spec.ts` | Playwright tests |

### Modified Files

| File | Change |
|---|---|
| `risk-orchestrator/.../Application.kt` | Wire RunManifestCapture (P0) |
| `risk-orchestrator/.../mapper/RunSnapshotMapper.kt` | Fix modelVersion null (P0) |
| `risk-orchestrator/.../model/RunComparison.kt` | Add `inputChanges: InputChangeSummary?` |
| `risk-orchestrator/.../service/RunComparisonService.kt` | Wire InputChangeDiffer |
| `risk-orchestrator/.../mapper/RunComparisonResponseMapper.kt` | Map inputChanges |
| `risk-orchestrator/.../routes/dtos/RunComparisonResponse.kt` | Add inputChanges field |
| `risk-orchestrator/.../routes/RunComparisonRoutes.kt` | Add quant diff endpoint |
| `gateway/.../routes/RunComparisonRoutes.kt` | Add quant diff proxy |
| `ui/src/types.ts` | Add new DTO types |
| `ui/src/api/runComparison.ts` | Add fetchMarketDataQuantDiff |
| `ui/src/components/GenericRunComparisonPanel.tsx` | Render InputChangesPanel |
| `ui/src/components/VaRAttributionPanel.tsx` | Fix misleading zeros |
| `ui/e2e/fixtures.ts` | Add mockInputChangesRoute() |

---

## Operational Runbook

### Verifying manifest capture is active

```sql
SELECT COUNT(*) FROM run_manifests WHERE captured_at > NOW() - INTERVAL '1 hour';
```
If zero during trading hours, check `Application.kt` wire.

### "Why does comparison show no input diff data?"

Check `valuation_jobs.manifest_id` for both jobs. If null, manifest capture was not active when those jobs ran.

### "Input diff says positions identical but VaR changed"

Check `parameterDiffs` — specifically `numSimulations`, `timeHorizonDays`, `monteCarloSeed`. Check `run_manifests.model_version` for both manifests.

### "Comparison shows PARTIAL manifests"

```sql
SELECT data_type, instrument_id, source_service, status, sourced_at
FROM run_manifest_market_data
WHERE manifest_id = :manifestId AND status = 'MISSING';
```

### Blob missing during diff

No recovery path exists for a missing blob (replay reads blobs too). Surface as unresolvable gap.

### Post-migration verification

```sql
SELECT indexname, pg_indexes.tablename
FROM pg_indexes
WHERE tablename IN ('run_manifest_market_data', 'run_market_data_blobs', 'valuation_jobs')
ORDER BY tablename, indexname;
```
Confirm all new indexes exist and are valid.
