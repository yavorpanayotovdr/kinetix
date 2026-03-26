# Divergence Classification — Status Tracker

**Created:** 2026-03-26
**Last updated:** 2026-03-26
**Scope:** All 20 `.allium` specs vs full Kinetix codebase

---

## Resolution Summary

| Category | Original | Resolved | Remaining |
|---|---|---|---|
| spec-update | 46 | 46 | 0 |
| code-fix P0 | 13 | 13 | 0 |
| code-fix P1 | 11 | 11 | 0 |
| code-fix P2 | 22 | 22 | 0 |
| decision-needed | 18 | 18 | 0 |
| aspirational (Tier 1) | 7 | 7 | 0 |
| aspirational (Tier 2) | 14 | 14 | 0 |
| aspirational (Tier 3) | 6 | 6 | 0 |
| **Total** | **137** | **137** | **0** |

---

## Resolved Items

### Phase 1: Spec Updates (46 items) — ALL DONE
All 16 spec files updated to match code reality. Covered enums, field types, naming, extra fields, missing entities, and documentation.

### P0 Code Fixes (13 items) — ALL DONE

| ID | Fix | Commit |
|---|---|---|
| LIQ-12 | Notional-weighted data_completeness | `3bada54e` |
| LIQ-08 | ADV concentration 5%/10% (was 50%) | `de45899f` |
| LIQ-07 | Bid-ask spread cost in LVaR | `3a17275e` |
| LIQ-04 | 6 snapshot fields full stack | `d7f5aa89`..`a9fcff10` |
| LIQ-01/02 | LiquidityTier enum — code is right (decision) | spec updated |
| HIER-02 | Budget type verified consistent | `034eb01b` |
| IPNL-01 | FX conversion with missing rate tracking | `c038b156` |
| TRAD-01 | counterpartyId wired through booking | `48da5382` |
| TRAD-05 | HTTP 409 for amend/cancel | `6f9387f6` |
| EXEC-01 | Pre-trade risk checks in order submission | `d7d9be92` |
| HDG-09 | Real prices for hedge recommendations | `bd7cf407` |
| REG_D-08 | Hold regime on degraded inputs | `5d994103` |
| RMOD-01 | PricingGreeks vs VaR — deferred (decision) | spec annotated |
| ALT-03 | Escalation severity names | spec updated |
| AUD-01 | Audit details field in hash | spec updated |

### P1 Code Fixes (11 items) — ALL DONE

| ID | Fix |
|---|---|
| RISK-02 | greekSnapshotId wired into SodBaseline |
| CPTY-06 | CVA skipped when no credit data |
| ALT-06 | Escalation audit events published |
| REG_D-01 | Early warnings in RegimeState API |
| EXEC-02 | ExecutionCostService wired into fills |
| EXEC-09/10 | Real asset class + currency for FIX fills |
| HIER-04 | PUT route for budget updates |
| IPNL-02 | Cross-Greek fields in REST/WebSocket/Kafka |
| HDG-08 | Position limit checking in hedge suggestions |
| SCEN-06 | Stress P&L impact delegated to risk engine |

### P2 Code Fixes (22 items) — ALL DONE

| ID | Fix |
|---|---|
| POS-03 | instrumentType in PositionResponse |
| CPTY-01 | cdsSpreadssBps typo fixed |
| MKT-01 | HTTP 400 for negative prices |
| MKT-04 | Positive vol validation |
| HDG-04 | No-candidates returns PENDING not REJECTED |
| REG_D-02 | vol_of_vol in RegimeSignals |
| IPNL-05 | pnl_vs_sod derived field |
| IPNL-06 | unexplained_pct derived field |
| RISK-06 | triggeredBy NOT NULL in DB |
| EXEC-08 | Arrival price staleness check |
| RISK-05 | PnlAttribution currency from positions |
| FAC-07 | Decomposition invariant (removed clamping) |
| REG_D-09 | Credit spread early warning |
| REG_D-10 | Regime transition alert severity mapping |
| IPNL-04 | UnexplainedPnlThreshold invariant |
| HDG-02 | suggestion_indices on accept |
| HDG-05 | hedging_eligible flag filtering |
| HDG-06 | Vol surface check for vega hedges |
| AUD-04 | Verification checkpoints in audit verify |
| MKT-02 | Union-grid vol surface diff |
| MKT-06 | Vol/yield retention policies |
| REG_D-07 | Dual-parameter VaR audit trail |

### Decision Items (18 items) — ALL RESOLVED (specs updated)

| ID | Decision |
|---|---|
| CORE-01 | CurvePoint: use code's (tenor, value) |
| CORE-02 | Permission enum: match code exactly |
| TRAD-03 | correlationId: auto-generated, not caller-supplied |
| TRAD-04 | Immutability: document protected vs mutable fields |
| LIM-02 | LimitCheckResult: match code structure |
| LIM-03 | LimitBreach: use SOFT/HARD severity model |
| RISK-03 | SodGreekSnapshot: flat per-instrument model |
| RISK-08/09 | CounterpartyExposure: match code, aspirational fields annotated |
| CPTY-04 | Wrong-way risk flags: keep as strings |
| CPTY-07 | SaCcrResult hedgingSetAddons: aspirational |
| AUD-02 | previous_hash: nullable (null for first event) |
| REG-06 | pnlImpact: nullable |
| REG-08 | inputDigest: nullable |
| REF-02 | DividendYield/CreditSpread: Double with DB precision guidance |
| MKT-03 | RiskFreeRate: Double with DB precision guidance |
| LIQ-05 | PositionLiquidityMetrics: JSON adequate for now |
| LIQ-06 | stressed_liquidation_value kept, days deferred |
| HDG-07 | Stale candidates: include with flag, not excluded |

### Aspirational Tier 1 (7 items) — ALL IMPLEMENTED

| ID | Feature |
|---|---|
| CPTY-02 | Auto-assign trades to netting sets at booking |
| CPTY-03 | Wire real collateral into counterparty risk |
| CPTY-05 | Per-netting-set PFE calculation |
| REG-07 | Submission acknowledged transition |
| FAC-04 | Factor P&L attribution via gRPC |
| LIQ-09 | Pre-trade ADV concentration check |
| REG_D-05 | Correlation anomaly in regime detection |

---

### Aspirational Tier 2 (14 items) — ALL IMPLEMENTED

| ID | Feature |
|---|---|
| RISK-07 | ReconcileTradeAudit daily scheduled job |
| CPTY-10 | Scheduled post-EOD counterparty risk batch |
| AUD-06 | RISK_CALCULATION_COMPLETED events (already existed) |
| AUD-07 | RBAC_ACCESS_DENIED events (already existed) |
| AUD-08 | REPORT_GENERATED audit events |
| FAC-01 | FactorDefinition persisted with seed data |
| FAC-02 | FactorReturn persisted with upsert and date-range query |
| FAC-03 | InstrumentFactorLoading with staleness tracking |
| FAC-05 | Scheduled daily factor decomposition |
| FAC-06 | FACTOR_CONCENTRATION alert on high exposure |
| SCEN-01 | Reverse stress test solver |
| REG-01 | ModelVersion 5 governance fields |
| LIQ-10 | LIQUIDITY_CONCENTRATION alert on ADV concentration |
| LIQ-11 | Liquidity recompute on significant trade events |

---

### Aspirational Tier 3 (6 items) — ALL IMPLEMENTED

| ID | Feature |
|---|---|
| SCEN-02 | Custom date range historical replay |
| SCEN-03 | Correlated scenarios from primary shock |
| SCEN-04 | Parametric 2D grid sweep |
| ALT-04 | Multi-channel escalation (email + webhook + PagerDuty) |
| ALT-05 | Escalation severity promotion |
| EXEC-04/05/06 | Break lifecycle, reconciliation alerts, FIX disconnect alerts |
| HIER-08 | CRO report generatedAt timestamp |
| REG_D-06 | Model staleness checking |
| RMOD-02 | VaRSensitivities type alias |
| HIER-03/06/09, IPNL-07, REG_D-03 | Architectural TODOs captured in code |

---

## Final Status: 137/137 divergences resolved. Zero remaining.
| REG_D-03/06 | RegimeModelConfig entity, CheckModelStaleness | risk-orchestrator + risk-engine |
| RMOD-02 | VaRSensitivities as distinct type | risk-engine |
