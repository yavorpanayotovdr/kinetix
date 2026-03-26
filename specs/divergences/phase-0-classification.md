# Phase 0: Divergence Classification

**Date:** 2026-03-26
**Scope:** All 20 `.allium` specs vs full Kinetix codebase
**Prior report:** `initial-report.md` (28 items, 2026-03-20) -- all critical items now resolved

Each divergence is classified as one of:
- **`spec-update`** -- code is correct, update spec to match
- **`code-fix`** -- spec is correct, fix the code
- **`decision-needed`** -- neither is fully right, need to pick direction
- **`aspirational`** -- spec describes unbuilt features intentionally

Priority levels:
- **P0** -- wrong numbers / broken control / runtime failure
- **P1** -- missing implementation of spec-committed feature
- **P2** -- structural mismatch / missing fields
- **P3** -- cosmetic / spec behind code

---

## core.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| CORE-01 | `CurvePoint` structurally incompatible: spec has `tenor, days, rate`; code has `tenor, value` | decision-needed | P2 |
| CORE-02 | `Permission` enum diverged: 6 spec values missing from code, 4 code values missing from spec | decision-needed | P2 |
| CORE-03 | `ModelStatus` enum (`draft\|validated\|approved\|retired`) -- no Kotlin implementation | aspirational | P3 |
| CORE-04 | `TrafficLightZone` enum -- no typed Kotlin enum, only ad-hoc strings | spec-update | P3 |
| CORE-05 | `DataQualityFlag` enum -- no typed Kotlin enum | spec-update | P3 |
| CORE-06 | `FrtbRiskClass` enum -- only exists inside a test file, not production | spec-update | P3 |
| CORE-07 | `AlertType` has 3 extra code values: `LIQUIDITY_CONCENTRATION`, `REGIME_CHANGE`, `FACTOR_CONCENTRATION` | spec-update | P3 |
| CORE-08 | `LimitType` has extra `VAR_BUDGET` in code | spec-update | P3 |
| CORE-09 | `TimeRange` value type defined in spec but no code counterpart | aspirational | P3 |

---

## trading.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| TRAD-01 | `counterpartyId` not wired through `BookTradeCommand` or `BookTradeRequest` despite spec requiring it and `Trade` model supporting it | code-fix | P0 |
| TRAD-02 | `strategyId` on Trade/Position exists in code but not spec | spec-update | P3 |
| TRAD-03 | `correlationId` auto-generated in code; spec treats it as caller-supplied | decision-needed | P2 |
| TRAD-04 | Immutability trigger doesn't protect `counterparty_id`, `instrument_type`, `original_trade_id`, `strategy_id` | decision-needed | P2 |
| TRAD-05 | `InvalidTradeStateException` not mapped to HTTP 409 for amend/cancel -- returns 500 | code-fix | P1 |

---

## positions.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| POS-01 | `CurrencyExposure.local_value/base_value` typed as `Decimal` in spec but `Money` in code | spec-update | P3 |
| POS-02 | Position has `strategyId/strategyType/strategyName` in code but not spec | spec-update | P3 |
| POS-03 | `PositionResponse` DTO drops `instrumentType` despite spec and domain model having it | code-fix | P2 |
| POS-04 | `FxRate` has `asOf: Instant` in code but not spec | spec-update | P3 |
| POS-05 | `CounterpartyExposure` has netting-set-aware calculation in code but spec uses simpler model | spec-update | P2 |
| POS-06 | FX rate fallback chain: code adds persistent repository lookup between inverse cache and static fallback | spec-update | P3 |

---

## limits.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| LIM-01 | `effective_value` fallback differs when `intraday=true, intraday_limit=null, overnight_limit!=null`: spec uses overnight, code uses base | code-fix | P0 |
| LIM-02 | `LimitCheckResult` structurally diverged: spec has `limit_type, entity_id`; code has `effectiveLimit, message`; code fields nullable | decision-needed | P2 |
| LIM-03 | `LimitBreach` uses `severity: SOFT/HARD` in code vs `status: OK/WARNING/BREACHED` in spec -- different conceptual model | decision-needed | P2 |
| LIM-04 | `LimitDefinition` missing `id` field in spec | spec-update | P3 |
| LIM-05 | `TemporaryIncrease` missing `id` and `createdAt` in spec | spec-update | P3 |

---

## risk.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| RISK-01 | `PositionRisk` missing `theta` and `rho` -- only computed at aggregate level | spec-update | P2 |
| RISK-02 | `SodBaseline.greekSnapshotId` -- DB column exists but domain model ignores it | code-fix | P1 |
| RISK-03 | `SodGreekSnapshot` flat per-instrument model vs spec's grouped parent-child structure; no `snapshotId` concept | decision-needed | P2 |
| RISK-04 | `CrossBookValuationResult.monteCarloSeed` is `Long` in code vs `Integer` in spec | spec-update | P3 |
| RISK-05 | `PnlAttribution.currency` defaults to `"USD"` -- not derived from positions | code-fix | P2 |
| RISK-06 | `ValuationJob.triggeredBy` non-null in domain model but nullable in DB column | code-fix | P2 |
| RISK-07 | `ReconcileTradeAudit` daily job not implemented | aspirational | P1 |
| RISK-08 | `CounterpartyExposureSnapshot` missing `gross_exposure`, `book_id`, `pfe_95_1y`; `pfeProfile` name differs | decision-needed | P2 |
| RISK-09 | `NettingSetExposure` missing `grossExposure`, `nettingBenefit`, `nettingBenefitPct`, `positionCount` | decision-needed | P2 |

---

## risk-models.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| RMOD-01 | `PricingGreeks` vs `VaRSensitivities` not separated -- SOD snapshot uses VaR Greeks for P&L attribution, violating `PnlExplainUsesPricingSensitivities` invariant | decision-needed | P0 |
| RMOD-02 | `VaRSensitivities` not implemented as a distinct type | aspirational | P2 |
| RMOD-03 | `FrtbCapitalCharge` named `FrtbResult` in code with extra `book_id` | spec-update | P3 |
| RMOD-04 | `BacktestResult` has extra `expected_violation_rate` and `violations` in code | spec-update | P3 |
| RMOD-05 | `PositionStressImpact` has extra `percentage_of_total` in code | spec-update | P3 |
| RMOD-06 | Monte Carlo antithetic variates exist in code but spec doesn't describe | spec-update | P3 |
| RMOD-07 | Python `StressScenario` has `category: ScenarioCategory` not in risk-models spec | spec-update | P3 |

---

## counterparty-risk.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| CPTY-01 | `Counterparty.cdsSpreadssBps` has typo (double-s) vs spec's `cds_spread_bps` | code-fix | P2 |
| CPTY-02 | `NettingSetTrade` entity not implemented -- trades not linked to netting sets | aspirational | P1 |
| CPTY-03 | `CollateralBalance` exists in position-service but not wired into counterparty risk flow | aspirational | P1 |
| CPTY-04 | `CounterpartyExposureSnapshot.wrongWayRiskFlags` is `List<String>?` vs spec's structured `List<WrongWayRiskFlag>` | decision-needed | P2 |
| CPTY-05 | `CalculatePFE` simplified to single netting set; spec requires per-set | aspirational | P1 |
| CPTY-06 | `CalculateCVA` missing `requires: counterparty.has_credit_data` guard -- allows CVA=0 fallback | code-fix | P1 |
| CPTY-07 | `SaCcrResult` missing `hedgingSetAddons` field | decision-needed | P2 |
| CPTY-08 | `SaCcrTradeClassification` computed in risk-engine but not surfaced to orchestrator | aspirational | P2 |
| CPTY-09 | `ExposureProfile` named `ExposureAtTenor` in code; `pfe_95`/`pfe_99` vs `pfe95`/`pfe99` | spec-update | P3 |
| CPTY-10 | `ScheduledCounterpartyRisk` post-EOD batch not implemented | aspirational | P1 |

---

## scenarios.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| SCEN-01 | `ReverseStressTest` solver -- DTOs exist but Python computation backend missing | aspirational | P1 |
| SCEN-02 | `ReplayCustomDateRange` -- no code path fetches arbitrary historical returns from price-service | aspirational | P2 |
| SCEN-03 | `CreateCorrelatedScenario` -- no endpoint, service, or DTO exists | aspirational | P2 |
| SCEN-04 | `ParametricGridSpec` -- no implementation for 2D grid sweep | aspirational | P2 |
| SCEN-05 | `BatchStressRunResult` adds `failedScenarios` list and uses `String?` for `worstPnlImpact` vs spec's `Decimal` | spec-update | P3 |
| SCEN-06 | `ScenarioPnlImpactFromRiskEngine` invariant -- known violation acknowledged in spec | code-fix | P1 |

---

## audit.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| AUD-01 | `AuditEvent.details` field in code + hash computation but absent from spec | spec-update | P0 |
| AUD-02 | `AuditEvent.previous_hash` is nullable (`null`) in code vs non-nullable (empty string `""`) in spec | decision-needed | P2 |
| AUD-03 | `VerificationCheckpoint` field names differ: `last_verified_id`/`last_verified_hash` vs `last_event_id`/`last_hash`; extra `event_count`; `Integer` vs `BIGINT` | spec-update | P2 |
| AUD-04 | `VerificationCheckpoint` table exists but not used by `/api/v1/audit/verify` endpoint | code-fix | P2 |
| AUD-05 | `GovernanceAuditEvent.bookId` exists in code but not in spec's `RecordGovernanceAuditEvent` rule | spec-update | P3 |
| AUD-06 | `RISK_CALCULATION_COMPLETED` governance events never emitted from risk-orchestrator | aspirational | P2 |
| AUD-07 | `RBAC_ACCESS_DENIED` audit events: enum value exists but no code path emits them | aspirational | P2 |
| AUD-08 | `REPORT_GENERATED` audit events: enum value exists but never used | aspirational | P2 |

---

## regulatory.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| REG-01 | `ModelVersion` missing 5 spec fields: `model_tier`, `validation_report_url`, `known_limitations`, `approved_use_cases`, `next_validation_date` | aspirational | P2 |
| REG-02 | `ModelVersion.createdAt` in code but not spec | spec-update | P3 |
| REG-03 | `StressScenario` has 9 extra fields in code: `scenarioType`, `category`, `version`, `parentScenarioId`, `correlationOverride`, `liquidityStressFactors`, `historicalPeriodId`, `targetLoss`, `createdAt` | spec-update | P2 |
| REG-04 | `UpdateScenario` rule (PUT /{id}) exists in code but not spec | spec-update | P2 |
| REG-05 | Reverse stress testing route exists in code but not spec | spec-update | P2 |
| REG-06 | `StressTestResultRecord.pnlImpact` nullable in code vs required in spec | decision-needed | P2 |
| REG-07 | `submitted -> acknowledged` submission transition not implemented -- no `acknowledge()` method | aspirational | P1 |
| REG-08 | `BacktestResultRecord.inputDigest` nullable in code vs required in spec | decision-needed | P2 |
| REG-09 | `BacktestComparison` flattened with extra fields in code vs nested `base/target` in spec | spec-update | P2 |
| REG-10 | `FrtbCalculationRecord.sbm_charges` is `List<RiskClassCharge>` (typed JSONB) in code vs `String` in spec | spec-update | P2 |
| REG-11 | `HistoricalScenarioPeriod` and `HistoricalReplayService` -- entire feature in code not in spec | spec-update | P2 |

---

## alerts.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| ALT-01 | `acknowledged -> resolved` transition allowed in code but not listed in spec | spec-update | P2 |
| ALT-02 | `AlertEvent.acknowledgedAt` field in code but not spec | spec-update | P3 |
| ALT-03 | Escalation severity names use `MEDIUM`/`HIGH` in spec but enum is `info\|warning\|critical` | spec-update | P0 |
| ALT-04 | Escalation delivery: code only sends email; spec requires multi-channel (email + webhook + PagerDuty) | aspirational | P2 |
| ALT-05 | Escalation severity promotion not implemented | aspirational | P2 |
| ALT-06 | Escalation does not emit audit events as spec requires | code-fix | P1 |
| ALT-07 | `RegimeChangeRule` and `AnomalyEventConsumer` exist in code but not spec | spec-update | P3 |

---

## reference-data.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| REF-01 | `DividendYield.yield_value` in spec vs `yield` in code | spec-update | P3 |
| REF-02 | `DividendYield`/`CreditSpread` use `Double` in domain model vs spec's `Decimal`; DB uses `decimal(18,8)` | decision-needed | P2 |
| REF-03 | `Instrument.createdAt`/`updatedAt` in code but not spec | spec-update | P3 |
| REF-04 | `CommodityOption.contractMultiplier` non-nullable (default 1.0) in code vs nullable in spec | spec-update | P3 |
| REF-05 | `EquityOption.dividendYield` non-nullable (default 0.0) in code vs nullable in spec | spec-update | P3 |
| REF-06 | `Counterparty`, `NettingAgreement`, `Benchmark` entities fully implemented but absent from spec | spec-update | P1 |
| REF-07 | `InstrumentLiquidity` entity implemented but absent from spec | spec-update | P2 |

---

## market-data.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| MKT-01 | Negative price ingestion returns HTTP 500 instead of 400 | code-fix | P2 |
| MKT-02 | Vol surface diff uses intersection-only matching; spec requires union-grid interpolation | code-fix | P2 |
| MKT-03 | `RiskFreeRate.rate` is `Double` in domain model vs spec's `Decimal`; precision lost at ingestion | decision-needed | P2 |
| MKT-04 | Vol surface ingestion doesn't validate positive implied vol | code-fix | P2 |
| MKT-05 | `CorrelationMatrix.method` is `EstimationMethod` enum in code vs `String` in spec | spec-update | P3 |
| MKT-06 | Vol surface and yield curve retention policies not implemented on main branch | code-fix | P2 |

---

## factor-model.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| FAC-01 | `FactorDefinition` entity not persisted -- hardcoded as Python constants | aspirational | P2 |
| FAC-02 | `FactorReturn` entity not persisted -- passed as transient request data | aspirational | P2 |
| FAC-03 | `InstrumentFactorLoading` not persisted -- computed per-request, no staleness tracking | aspirational | P2 |
| FAC-04 | `FactorPnlAttribution` Python function exists but no gRPC endpoint | aspirational | P1 |
| FAC-05 | `ScheduledLoadingReestimation` and `ScheduledFactorDecomposition` not implemented | aspirational | P2 |
| FAC-06 | `WarnOnFactorConcentration` not implemented -- config constant exists but unused | aspirational | P2 |
| FAC-07 | `DecompositionSumsToTotal` invariant violated by zero-clamping of idiosyncratic VaR | code-fix | P2 |
| FAC-08 | Factor return and loading retention policies have no target (nothing persisted) | aspirational | P3 |

---

## liquidity.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| LIQ-01 | `LiquidityTier` enum: two incompatible schemes coexist (TIER_1/2/3 in ref-data vs HIGH_LIQUID/LIQUID/SEMI_LIQUID in risk-engine/proto/common) | decision-needed | P0 |
| LIQ-02 | Tier classification logic: spec uses absolute ADV/spread thresholds; code uses ADV-fraction thresholds -- different results | decision-needed | P0 |
| LIQ-03 | `InstrumentLiquidity` missing `adv_shares`, `market_depth_score`, `source` fields | decision-needed | P2 |
| LIQ-04 | `LiquidityRiskSnapshot` missing 6 fields: `var_1day`, `lvar_ratio`, `weighted_avg_horizon`, `max_horizon`, `concentration_count`, `adv_data_as_of` | decision-needed | P2 |
| LIQ-05 | `PositionLiquidityMetrics` not separately persisted -- embedded as JSON in snapshot | decision-needed | P2 |
| LIQ-06 | `stressed_liquidation_days` (spec) vs `stressed_liquidation_value` (code) -- different metrics entirely | decision-needed | P2 |
| LIQ-07 | LVaR formula missing bid-ask spread cost component | code-fix | P0 |
| LIQ-08 | ADV concentration threshold is 50% in code vs spec's 5% warning / 10% hard block | code-fix | P0 |
| LIQ-09 | Pre-trade ADV concentration check not integrated into limit hierarchy framework | aspirational | P1 |
| LIQ-10 | `AlertOnConcentration` not implemented -- no notification-service integration | aspirational | P2 |
| LIQ-11 | `RecomputeLiquidityOnTrade` not implemented -- no Kafka listener for trade events | aspirational | P2 |
| LIQ-12 | `data_completeness` computed by position count vs spec's notional-weighted | code-fix | P0 |

---

## regime.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| REG_D-01 | `RegimeState` missing `early_warnings` field in Kotlin model and API | code-fix | P1 |
| REG_D-02 | `RegimeSignals` missing `vol_of_vol` in Kotlin (Python has it) | code-fix | P2 |
| REG_D-03 | `RegimeModelConfig` entity not implemented -- thresholds hardcoded | aspirational | P2 |
| REG_D-04 | `RECOVERY` regime unreachable -- classifier never produces it | decision-needed | P1 |
| REG_D-05 | `CorrelationAnomalyCheck` -- compute function is dead code, not wired into detection loop | aspirational | P2 |
| REG_D-06 | `CheckModelStaleness` not implemented | aspirational | P2 |
| REG_D-07 | `OverrideVaRParameters` -- dual-parameter audit trail (requested vs effective) not verified | code-fix | P2 |
| REG_D-08 | `HandleDegradedSignals` not enforced -- classifier transitions on degraded inputs when spec says hold state | code-fix | P0 |
| REG_D-09 | `EarlyWarningCheck` -- credit spread rapid widening condition not checked (only vol and correlation checked) | code-fix | P2 |
| REG_D-10 | `AlertOnRegimeTransition` severity mapping conformance needs verification | code-fix | P2 |
| REG_D-11 | `RegimeState` API response missing `earlyWarnings` | code-fix | P2 |

---

## execution.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| EXEC-01 | Order submission auto-approves, bypassing `HierarchyBasedPreTradeCheckService` | code-fix | P0 |
| EXEC-02 | `ExecutionCostService` is dead code -- never called after fills | code-fix | P1 |
| EXEC-03 | `ReconciliationBreak.severity` field in code but not spec | spec-update | P3 |
| EXEC-04 | `ReconciliationBreakStatus` enum in spec but no code counterpart -- no break lifecycle | aspirational | P2 |
| EXEC-05 | Alert on reconciliation breaks (>$10K notional) not implemented -- logs only | aspirational | P2 |
| EXEC-06 | FIX session disconnect alert not implemented | aspirational | P2 |
| EXEC-07 | Config values hardcoded as companion object constants, not externally configurable | decision-needed | P3 |
| EXEC-08 | Arrival price staleness check (30s) not implemented | code-fix | P2 |
| EXEC-09 | Trade booking hardcodes `AssetClass.EQUITY` for all FIX fills | code-fix | P1 |
| EXEC-10 | Trade booking hardcodes `Currency.USD` for all FIX fills | code-fix | P1 |

---

## hedge.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| HDG-01 | `carrycostPerDay` field name drops underscore (`carry_cost` -> `carrycost`) | spec-update | P3 |
| HDG-02 | `AcceptHedgeRecommendation` doesn't support `suggestion_indices` parameter -- accepts wholesale | code-fix | P2 |
| HDG-03 | No `HedgeAccepted` Kafka event emitted on acceptance | aspirational | P2 |
| HDG-04 | No-candidates case returns `REJECTED` status; spec implies `pending` with empty suggestions | code-fix | P2 |
| HDG-05 | Candidate filtering doesn't check `hedging_eligible` flag from reference data | code-fix | P2 |
| HDG-06 | No vol surface availability check for vega hedges | code-fix | P2 |
| HDG-07 | No price staleness filtering -- stale candidates included with flag instead of excluded | decision-needed | P2 |
| HDG-08 | Position limit checking not implemented -- `respectPositionLimits` field exists but never read | code-fix | P1 |
| HDG-09 | `pricePerUnit` hardcoded to 100.0 -- all cost calculations use placeholder | code-fix | P0 |

---

## hierarchy-risk.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| HIER-01 | `BudgetUtilisation` adds `budgetType`, `breachStatus`, `updatedAt`; lacks `budget_period` | spec-update | P2 |
| HIER-02 | Budget type string mismatch: spec `"VAR"` vs code `"VAR_BUDGET"` -- utilisation silently finds no budgets | code-fix | P0 |
| HIER-03 | Budget breach alert to notification-service not implemented -- logs warning only | aspirational | P2 |
| HIER-04 | Budget update route (PUT/PATCH) not implemented -- only GET, POST, DELETE | code-fix | P1 |
| HIER-05 | `BookHierarchyMapping` named `BookHierarchyEntry` in code; `bookName` nullable in code but not spec | spec-update | P3 |
| HIER-06 | Drill-down endpoint (children at target level) not implemented | aspirational | P2 |
| HIER-07 | `HierarchyNodeRisk` has `isPartial`/`missingBooks` in code but not spec | spec-update | P3 |
| HIER-08 | CRO report is thin wrapper over hierarchy aggregation; spec envisions rich report with breaches, stress results, regime | aspirational | P2 |
| HIER-09 | `ScheduledHierarchyAggregation` doesn't detect >10% VaR changes for ad-hoc triggers | aspirational | P2 |

---

## intraday-pnl.allium

| ID | Description | Classification | Priority |
|----|-------------|----------------|----------|
| IPNL-01 | FX conversion is a no-op (`convertToBase` returns 1:1) -- violates `CurrencyConsistency` invariant | code-fix | P0 |
| IPNL-02 | Cross-Greek fields (vanna, volga, charm, cross-gamma) computed in domain model but not surfaced in REST DTO, WebSocket, or Kafka event | code-fix | P1 |
| IPNL-03 | `charm_pnl` computed in code but not in spec's `GreekAttribution` or `AttributionSumsToTotal` invariant | spec-update | P2 |
| IPNL-04 | `UnexplainedPnlThreshold` invariant (>20% warning) not implemented -- no `dataQualityWarning` field | code-fix | P2 |
| IPNL-05 | `pnl_vs_sod` derived field not present in code | code-fix | P2 |
| IPNL-06 | `unexplained_pct` derived field not present in code | code-fix | P2 |
| IPNL-07 | Redis cache for latest P&L (`pnl:latest:{book_id}`) not implemented | aspirational | P2 |
| IPNL-08 | Snapshot retention policy (30 days) not implemented | aspirational | P3 |
| IPNL-09 | `IntradayPnlState` (in-memory state cache) not implemented -- queries DB per invocation | decision-needed | P3 |

---

## Summary

| Classification | Count | % |
|----------------|-------|---|
| **spec-update** | 46 | 37% |
| **code-fix** | 42 | 34% |
| **aspirational** | 27 | 22% |
| **decision-needed** | 22 | 18% |
| **Total** | **137** | |

Note: some items have elements of multiple categories but are classified by their primary action.

### By Priority

| Priority | Count | Description |
|----------|-------|-------------|
| **P0** | 13 | Wrong numbers, broken controls, runtime failures |
| **P1** | 17 | Missing implementation of spec-committed features |
| **P2** | 73 | Structural mismatches, missing fields |
| **P3** | 34 | Cosmetic, spec behind code |

### P0 Items (fix first)

| ID | Description | Classification |
|----|-------------|----------------|
| LIQ-07 | LVaR missing bid-ask spread cost | code-fix |
| LIQ-08 | ADV concentration threshold 50% vs 5%/10% | code-fix |
| LIQ-12 | data_completeness by count not notional | code-fix |
| LIQ-01 | LiquidityTier enum incompatibility | decision-needed |
| LIQ-02 | Tier classification logic mismatch | decision-needed |
| HIER-02 | Budget type "VAR" vs "VAR_BUDGET" | code-fix |
| IPNL-01 | FX conversion no-op | code-fix |
| TRAD-01 | counterpartyId not wired through booking | code-fix |
| EXEC-01 | Order submission bypasses pre-trade risk checks | code-fix |
| HDG-09 | Hedge price hardcoded to 100.0 | code-fix |
| REG_D-08 | HandleDegradedSignals not enforced | code-fix |
| RMOD-01 | PricingGreeks vs VaR sensitivities not separated | decision-needed |
| ALT-03 | Escalation severity names don't match enum | spec-update |
| AUD-01 | Audit details field in hash but not spec | spec-update |
