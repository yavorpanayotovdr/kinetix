# Divergence Follow-Up Plan

**Date:** 2026-03-23
**Source:** `specs/divergences/initial-report.md` (28 issues)

This plan categorises every divergence into one of three buckets and defines the exact fix for each.

---

## Bucket 1: Spec Is Wrong, Code Is Right

These are cases where the LLM lossy-summarised the code during spec extraction. The specs must be updated to match the code exactly.

### 1.1 Source Enum Corrections (Issues #1–4)

The spec invented a generic `system` variant instead of capturing the actual domain-specific values.

| Spec file | Enum | Current spec values | Correct values (from code) |
|-----------|------|--------------------|-----------------------------|
| `core.allium` | `PriceSource` | `system \| bloomberg \| reuters \| manual` | `bloomberg \| reuters \| exchange \| internal \| manual` |
| `core.allium` | `RateSource` | `system \| bloomberg \| reuters \| manual` | `bloomberg \| reuters \| central_bank \| internal \| manual` |
| `core.allium` | `VolatilitySource` | `system \| bloomberg \| reuters \| manual` | `bloomberg \| reuters \| exchange \| internal \| manual` |
| `core.allium` | `ReferenceDataSource` | `system \| bloomberg \| reuters \| manual` | `bloomberg \| reuters \| rating_agency \| internal \| manual` |

**Action:** Update all four enums in `specs/core.allium` to match the Kotlin enums in `common/`.

### 1.2 ManifestStatus Terminal States (Issue #5)

- **Spec:** `inputs_frozen | partial | outputs_finalized`
- **Code:** `INPUTS_FROZEN | COMPLETE | PARTIAL | FAILED`

The spec used `outputs_finalized` instead of `complete` and omitted the `failed` terminal state.

**Action:** Update `specs/core.allium` ManifestStatus to `inputs_frozen | complete | partial | failed`.

### 1.3 RunLabel Missing Values (Issue #14)

- **Spec:** `official_eod | sod`
- **Code:** `ADHOC | SOD | INTRADAY | OVERNIGHT | PRE_CLOSE | OFFICIAL_EOD | SUPERSEDED_EOD`

The spec captured only 2 of 7 values.

**Action:** Update `specs/core.allium` RunLabel to include all 7 values.

### 1.4 TriggerType Missing Value (Issue #15)

- **Spec:** `on_demand | trade_event | price_event | scheduled`
- **Code adds:** `MODEL_COMPARISON`

**Action:** Add `model_comparison` to TriggerType in `specs/core.allium`.

### 1.5 Manifest Retention Duration (Issue #17)

- **Spec:** `365.days`
- **Code:** `2555` days (7 years)

**Action:** Update `specs/risk.allium` manifest_retention config from `365.days` to `2555.days`.

### 1.6 PNL_THRESHOLD Metric Extraction (Issue #18)

- **Spec:** extracts `event.dailyPnl`
- **Code:** extracts `event.expectedShortfall`

**Action:** Update `specs/alerts.allium` PNL_THRESHOLD metric to reference `event.expectedShortfall`.

### 1.7 Audit Hash Computation — Missing Field (Issue #9)

- **Code:** includes `receivedAt` as the first field in the SHA-256 hash input
- **Spec:** omits `receivedAt` from the hash field list

**Action:** Add `receivedAt` as the first field in the hash computation description in `specs/audit.allium`.

### 1.8 RunManifest.num_simulations Optionality (Issue #23)

- **Spec:** `num_simulations: Integer?` (optional)
- **Code:** `val numSimulations: Int` (required, non-nullable)

**Action:** Change `specs/risk.allium` `num_simulations` from `Integer?` to `Integer`.

### 1.9 PositionPnlAttribution Missing Fields (Issue #24)

- **Code adds:** `assetClass: String` and `unexplainedPnl: BigDecimal`
- **Spec:** omits both

**Action:** Add `asset_class: String` and `unexplained_pnl: Decimal` to the PositionPnlAttribution entity in `specs/risk.allium`.

### 1.10 PnlAttribution Missing Fields (Issue #8 moderate / #16)

- **Code fields:** `deltaPnl`, `gammaPnl`, `vegaPnl`, `thetaPnl`, `rhoPnl`, `unexplainedPnl`, `calculatedAt`
- **Spec fields:** `delta_attribution`, `gamma_attribution`, etc. — missing `unexplainedPnl` and `calculatedAt`

**Action:** Add `unexplained_pnl: Decimal` and `calculated_at: Timestamp` to PnlAttribution in `specs/risk.allium`. Naming convention addressed in Bucket 3 below.

### 1.11 Alert Contributors Type (Issue #25)

- **Spec:** `contributors: List<PositionBreakdownItem>?`
- **Code:** `contributors: String? = null`

**Action:** Update `specs/alerts.allium` contributors field type from `List<PositionBreakdownItem>?` to `String?`.

### 1.12 BacktestResultRecord Window Dates (Issue #26)

- **Spec:** `window_start: String, window_end: String` (required)
- **Code:** `windowStart: LocalDate? = null, windowEnd: LocalDate? = null` (optional)

**Action:** Update `specs/regulatory.allium` to `window_start: Date?, window_end: Date?`.

### 1.13 EquityFuture/CommodityFuture Missing Currency (Issue #27)

- **Code:** both types have a `currency: String` field
- **Spec:** omits it

**Action:** Add `currency: String` to both EquityFuture and CommodityFuture entities in `specs/reference-data.allium`.

### 1.14 InterestRateSwap Frequency Types (Issue #20)

- **Spec:** `fixed_frequency: String?, float_frequency: String?`
- **Code:** `fixedFrequency: Int = 2, floatFrequency: Int = 4`

**Action:** Update `specs/reference-data.allium` to `fixed_frequency: Integer = 2, float_frequency: Integer = 4`.

### 1.15 CommodityFuture Field Name (Issue #21)

- **Spec:** `underlying_id: String`
- **Code:** `commodity: String`

**Action:** Rename field in `specs/reference-data.allium` from `underlying_id` to `commodity`.

### 1.16 OptionType Kotlin Gap (Issue #28)

- **Spec:** defines `OptionType` in `core.allium` as a shared enum
- **Code:** exists in Python only (`risk-engine/models.py`), not in Kotlin `common` module

**Action:** Add a `@guidance` annotation in `specs/core.allium` noting that `OptionType` is currently Python-only. Optionally flag as a future Kotlin addition.

### 1.17 Missing Enum Implementations (Issues #10–12)

The spec defines `ExerciseStyle`, `BondSeniority`, and `SwapDirection` as typed enums, but all three are plain strings throughout Kotlin, Python, and Proto.

**Action:** Downgrade these from `enum` to `String` in the specs to match reality, with a `@guidance` note that typed enums would be a future improvement.

### 1.18 LimitStatus Naming (Issue #13)

- **Spec:** `LimitStatus`
- **Code:** `LimitCheckStatus` (Kotlin only, correct values: `OK | WARNING | BREACHED`)

**Action:** Rename to `LimitCheckStatus` in `specs/limits.allium` to match the Kotlin enum name.

---

## Bucket 2: Code Is Buggy, Spec Captured Intent

These are real defects in the codebase that the spec either described correctly or exposed by contrast.

### 2.1 Broken Immutability Trigger (Issue #6) — CRITICAL

**Problem:** Migration V10 (`V10__add_trade_event_immutability_trigger.sql`) creates a trigger that references `OLD.portfolio_id`. Migration V13 (`V13__rename_portfolio_id_to_book_id.sql`) renames the column to `book_id`. The trigger was never updated and is broken at runtime — any UPDATE on `trade_events` will fail with a column-not-found error.

**Fix:**
1. Write a new Flyway migration (next version) that drops and recreates the immutability trigger with `OLD.book_id` instead of `OLD.portfolio_id`.
2. Write an integration test that attempts to update a non-status field on `trade_events` and asserts the trigger rejects it.
3. Write a test that updates the `status` field and asserts it succeeds.

**Spec action:** No change — the spec correctly describes the intended behaviour.

### 2.2 EOD Self-Promotion Bypass (Issue #8) — CRITICAL

**Problem:** `EodPromotionService.kt` line 38 checks:
```kotlin
if (job.triggeredBy != null && job.triggeredBy == promotedBy)
```
When `triggeredBy` is null, the four-eyes check is skipped entirely, allowing anyone to promote their own job.

**Fix:**
1. Add a failing unit test: when `triggeredBy` is null and `promotedBy` is any user, promotion should still succeed (since there's no originator to conflict with) — OR — should be rejected. **Decision needed:** Is null `triggeredBy` (e.g., a scheduled job with no human originator) exempt from four-eyes? If scheduled jobs have no human owner, self-promotion is arguably fine. If four-eyes must always apply, a scheduled job should record the system/scheduler as `triggeredBy`.
2. If the decision is that null-triggered jobs are exempt (current behaviour is intentional), update the spec to document this exception.
3. If the decision is strict four-eyes always, change the code to:
   ```kotlin
   if (job.triggeredBy == promotedBy) {
       throw EodPromotionException.SelfPromotion(promotedBy)
   }
   ```
   and ensure scheduled jobs populate `triggeredBy` with a system identifier.

**Spec action:** Pending decision — either update the spec to document the null exception, or keep the spec as-is and fix the code.

### 2.3 LimitCheckService vs LimitHierarchyService Inconsistency (Issue #7)

**Problem:** Two services implement limit checks with different strategies:
- `LimitCheckService` — hardcoded `TradeLimits` config, no DB lookup, no hierarchy, no temporary increases.
- `LimitHierarchyService` — full DB-driven hierarchy (FIRM→DESK→TRADER→COUNTERPARTY), temporary increases, warning thresholds.

The spec describes the `LimitHierarchyService` behaviour. The original `LimitCheckService` predates the hierarchy implementation and was never migrated.

**Fix:**
1. Audit all call sites of `LimitCheckService` to understand which code paths still use hardcoded limits.
2. Migrate those call sites to use `LimitHierarchyService` (or a unified interface).
3. Deprecate and remove `LimitCheckService` once all callers are migrated.
4. Add acceptance tests verifying that pre-trade checks use the DB-driven hierarchy.

**Spec action:** No change — the spec correctly describes the target behaviour.

### 2.4 Warning Threshold Operator Inconsistency (Issue #19)

**Problem:** The divergence report flagged `>` vs `>=` at the 80% warning boundary between `LimitCheckService` and `LimitHierarchyService`.

**Investigation result:** `LimitHierarchyService` uses `>=` which matches the spec. `LimitCheckService` may use a different operator. This becomes moot once 2.3 is resolved (LimitCheckService removed).

**Fix:** Resolved by completing 2.3 above.

---

## Bucket 3: Naming and Abstraction Differences

Neither side is wrong — these require a convention decision and then consistent application to both spec and code.

### 3.1 PnlAttribution Field Naming (Issue #16)

- **Spec:** `delta_attribution`, `gamma_attribution`, `vega_attribution`, `theta_attribution`, `rho_attribution`
- **Code:** `deltaPnl`, `gammaPnl`, `vegaPnl`, `thetaPnl`, `rhoPnl`

The semantic difference is "attribution" (what caused the P&L) vs "pnl" (the P&L amount from that Greek). In practice these are the same value.

**Decision needed:** Which naming convention?
- `*_pnl` is shorter, matches the code, and is unambiguous in context.
- `*_attribution` is more precise about what the value represents.

**Recommendation:** Adopt `*_pnl` (match the code). Update the spec.

### 3.2 Job Phase Names (Issue #22)

- **Spec:** `CALCULATE_VAR`, `RECORD`
- **Code:** `VALUATION`, `PUBLISH_RESULT`

**Decision needed:**
- `VALUATION` is more accurate than `CALCULATE_VAR` since the phase covers more than just VaR.
- `PUBLISH_RESULT` is more descriptive than `RECORD`.

**Recommendation:** Adopt the code's names. Update the spec.

---

## Execution Order

| Priority | Items | Effort | Risk |
|----------|-------|--------|------|
| 1 — Fix code bugs | 2.1 (broken trigger), 2.2 (self-promotion) | Small | Runtime failures / security gap |
| 2 — Resolve design debt | 2.3 + 2.4 (limit service unification) | Medium | Behavioural inconsistency |
| 3 — Naming decisions | 3.1, 3.2 | Trivial | None — just pick and update |
| 4 — Spec corrections | All of Bucket 1 (1.1–1.18) | Small | No code changes, spec-only |

Bucket 2 items should be implemented with TDD: write failing tests first, then fix. Bucket 1 and 3 are spec-only text changes with no code impact.
