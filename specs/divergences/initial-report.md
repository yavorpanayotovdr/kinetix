# Allium Spec-to-Code Divergence Report

**Date:** 2026-03-20
**Scope:** All 11 `.allium` specs in `specs/` vs full Kinetix codebase
**Method:** Parallel agent exploration of each spec against its implementation

---

## Critical (5) — Enum value mismatches

| # | Spec | Code | Issue |
|---|------|------|-------|
| 1 | `core.allium` PriceSource | `common/.../PriceSource.kt` | Spec: `system\|bloomberg\|reuters\|manual`. Code: `BLOOMBERG\|REUTERS\|EXCHANGE\|INTERNAL\|MANUAL`. Missing `EXCHANGE`, `INTERNAL`; ghost `system`. |
| 2 | `core.allium` RateSource | `common/.../RateSource.kt` | Same pattern — code has `CENTRAL_BANK\|INTERNAL` instead of `system`. |
| 3 | `core.allium` VolatilitySource | `common/.../VolatilitySource.kt` | Code has `EXCHANGE\|INTERNAL` instead of `system`. |
| 4 | `core.allium` ReferenceDataSource | `common/.../ReferenceDataSource.kt` | Code has `RATING_AGENCY\|INTERNAL` instead of `system`. |
| 5 | `core.allium` ManifestStatus | `risk-orchestrator/.../ManifestStatus.kt` | Spec: `inputs_frozen\|partial\|outputs_finalized`. Code: `INPUTS_FROZEN\|COMPLETE\|PARTIAL\|FAILED`. Different terminal states. |

## Critical (4) — Behavioural divergences

| # | Spec | Code | Issue |
|---|------|------|-------|
| 6 | `trading.allium` immutability trigger | `position-service V10 migration` | Trigger references `OLD.portfolio_id` but V13 renamed column to `book_id`. **Trigger is broken at runtime.** |
| 7 | `limits.allium` pre-trade checks | `LimitCheckService.kt` | Spec says checks query `LimitDefinition` from DB. Code uses hard-coded `TradeLimits` config — no DB lookup, no temporary increases, no hierarchy. |
| 8 | `risk.allium` EOD four-eyes | `EodPromotionService.kt:38` | Spec requires `promoted_by != job.triggered_by` always. Code only checks when `triggeredBy != null`, allowing self-promotion on null. |
| 9 | `audit.allium` hash computation | `AuditHasher.kt:9-23` | Code includes `receivedAt` as first field in hash. Spec omits it from the hash input list. |

## Critical (4) — Missing enum implementations

| # | Spec enum | Issue |
|---|-----------|-------|
| 10 | `ExerciseStyle` | No implementation in codebase |
| 11 | `BondSeniority` | Python uses plain string, no enum type |
| 12 | `SwapDirection` | Python uses plain string, no enum type |
| 13 | `LimitStatus` | No implementation found |

## Moderate (8)

| # | Spec | Code | Issue |
|---|------|------|-------|
| 14 | `core.allium` RunLabel | `RunLabel.kt` | Spec has 2 values; code has 7 (`ADHOC`, `INTRADAY`, `OVERNIGHT`, `PRE_CLOSE`, `SUPERSEDED_EOD` missing from spec) |
| 15 | `core.allium` TriggerType | `TriggerType.kt` | Code has extra `MODEL_COMPARISON` not in spec |
| 16 | `risk.allium` PnlAttribution fields | `PnlAttribution.kt` | Spec: `delta_attribution`. Code: `deltaPnl`. Different naming semantics (attribution vs pnl) |
| 17 | `risk.allium` manifest_retention | `ScheduledManifestRetentionJob.kt` | Spec: 365 days. Code: 2555 days (7 years) |
| 18 | `alerts.allium` PNL_THRESHOLD metric | `MetricExtractor.kt:18` | Spec says extract `event.dailyPnl`. Code extracts `event.expectedShortfall` |
| 19 | `limits.allium` warning threshold | `LimitCheckService` vs `LimitHierarchyService` | Inconsistent `>` vs `>=` at 80% boundary between two services |
| 20 | `reference-data.allium` InterestRateSwap | `InterestRateSwap.kt` | `fixedFrequency`/`floatFrequency` are `Int` in code, `String?` in spec |
| 21 | `reference-data.allium` CommodityFuture | `CommodityFuture.kt` | Spec field `underlying_id`; code field `commodity` |

## Minor (7)

| # | Spec | Issue |
|---|------|-------|
| 22 | `risk.allium` | Job phase names differ (CALCULATE_VAR vs VALUATION, RECORD vs PUBLISH_RESULT) |
| 23 | `risk.allium` | RunManifest.num_simulations is optional in spec, required in DB |
| 24 | `risk.allium` | PositionPnlAttribution missing `assetClass` and `unexplainedPnl` fields from code |
| 25 | `alerts.allium` | contributors stored as `String` in code, `List<PositionBreakdownItem>` in spec |
| 26 | `regulatory.allium` | BacktestResultRecord window dates are `LocalDate?` in code, `String` in spec |
| 27 | `reference-data.allium` | EquityFuture/CommodityFuture have extra `currency` field in code |
| 28 | `core.allium` | OptionType exists in Python only, not in Kotlin common module |

## Fully Aligned (no issues)

- **risk-models.allium** — all algorithms, formulas, config constants, and thresholds match Python implementation exactly
- **positions.allium** — all entity fields, derived values, and rule logic match
- **regulatory.allium** — state machines and four-eyes logic match (minor type differences noted above)

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 13 |
| Moderate | 8 |
| Minor | 7 |
| **Total** | **28** |

The broken immutability trigger (#6) is a runtime bug — the V10 trigger references `portfolio_id` which was renamed to `book_id` in V13.
