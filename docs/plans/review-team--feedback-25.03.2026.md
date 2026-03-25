# Review Team Feedback: Trader Roadmap Proposal

**Date:** 25 March 2026
**Proposal by:** Marcus (Senior Trader)
**Reviewed by:** 11-person specialist team (Trader, Architect, Quant, QA, UX Designer, Data Analyst, Security Engineer, Compliance Officer, Performance Engineer, SRE, Product Manager)

---

## Executive Summary

Marcus proposed a 12-item, 3-tier roadmap to take the Kinetix platform from its current state to production-ready for real trading desks. The review team found the instincts directionally correct but identified critical issues with scoping, sequencing, and several items that are already built or partially built. The most significant cross-cutting finding is a **foundational semantic problem**: the Greeks in `greeks.py` are VaR sensitivities (dVaR/dX), not pricing sensitivities (dPV/dX), which invalidates naively building P&L explain on top of them.

### Key Consensus Points

1. **RBAC must move to Tier 0/1** — every reviewer flagged this. The JWT infrastructure exists but `devModule()` routes are unauthenticated. This is a stop-ship issue, not a Tier 2 feature.
2. **P&L explain is partially built** — the intraday P&L decomposition (delta, gamma, vega, theta, rho, unexplained) already streams via WebSocket. The gap is cross-gamma, and — critically — `volChange` and `rateChange` are hardcoded to zero.
3. **Key rate durations are the highest-value genuine gap** — universally agreed across trader, quant, architect, and compliance.
4. **The `computePnlImpact` stub is a data integrity bug** — scenario P&L sums raw shock values instead of calling the risk engine. Must be fixed before any scenario work ships.
5. **Multi-leg trade support is drastically underscoped at Tier 2** — should be Tier 3 with a design spike.
6. **SA-CCR should be elevated to Tier 2** if the platform is used for regulatory capital.

---

## The Foundational Issue: Greeks Semantics

**Identified by:** Quant, Data Analyst, Performance Engineer

The most consequential finding across all reviews: `greeks.py` computes sensitivities of portfolio **VaR**, not portfolio **PV**. Delta is `(VaR_up - VaR_base) / PRICE_BUMP`, not `(PV_up - PV_base) / PRICE_BUMP`. These are categorically different measures.

- **Impact on P&L explain (Item 1):** Building Taylor expansion P&L attribution on VaR sensitivities will produce numbers that look plausible on normal days but systematically diverge from actual P&L on large-move days. The residual will absorb the error.
- **Impact on KRD (Item 2):** DV01 computed via VaR bumps is not the same as bond price sensitivity to rate changes.
- **Resolution:** Use the analytical BS Greeks from `black_scholes.py` (already implemented and correct) for options, `bond_dv01` for bonds, and `swap_dv01` for swaps. The `position_greeks` path in `ValuationResult` is the correct foundation. Keep VaR sensitivities in `greeks.py` for their intended purpose (risk attribution within the VaR framework).

**This must be resolved architecturally before Items 1 and 2 can produce defensible output.**

---

## Item-by-Item Review

### Tier 1: Do These Now

#### Item 1 — Full P&L Explain

| Perspective | Assessment |
|---|---|
| **Trader** | Already partially built. Intraday P&L with delta/gamma/vega/theta/rho/unexplained streams via WebSocket. The real gap is cross-gamma and fixing the zero-filled vol/rate changes. **Demote to a targeted fix.** |
| **Quant** | Taylor expansion methodology is correct but must use pricing sensitivities, not VaR sensitivities. Cross-gamma (dS_i * dS_j) requires correlated multi-asset test fixtures. Volga computation in `greeks.py` uses forward-differencing (first-order error) while `cross_greeks.py` has correct analytical volga — latent inconsistency. |
| **Architect** | Fits within existing `risk-orchestrator` service boundary. One Flyway migration to add vanna/volga/charm P&L columns. **Prerequisite:** fix `buildAttributionInputs()` where `volChange` and `rateChange` are hardcoded to `BigDecimal.ZERO`. |
| **QA** | Property-based test needed: `sum(all_components) == total_pnl` to 10 decimal places. Edge case: what happens when Greeks are stale and market moves 15%? Need test for `residual/totalPnl > 20%` flagging. |
| **UX** | Extends existing P&L tab. Replace diverging bar chart with true cumulative waterfall. Add "Expand Greeks" toggle for cross-gamma/volga/charm sub-segments. |
| **Performance** | Taylor expansion is O(n) arithmetic — single-digit milliseconds for 500 positions. Not a performance concern. The I/O-bound part is supplying live vol/rate deltas on every tick. |
| **Data Analyst** | SOD Greek snapshot must be locked before market open. Current `sod_baselines` stores only VaR/ES, not Greeks. Need immutable SOD Greek snapshot table. `daily_pnl_summary` continuous aggregate must be rebuilt (drop/recreate) to add new columns. |
| **Compliance** | P&L explain is a model under SR 11-7. Must be registered in `ModelRegistry` with methodology documentation before production use. FRTB PLAT requires this for IMA eligibility. |

**Consensus scope:** Fix `volChange`/`rateChange` zero-fill. Add cross-gamma term. Register as model in governance. One-sprint effort.

---

#### Item 2 — Key Rate Durations

| Perspective | Assessment |
|---|---|
| **Trader** | **Highest-priority genuine gap.** Flat DV01 tells you nothing about a 2s10s steepener. Must come before expanded scenarios to make rates stress tests meaningful. |
| **Quant** | Requires replacing `YieldCurveData` linear interpolation with monotone cubic spline. Per-tenor bump should use tent functions (Reitano/BARRA standard). `bond_pv` must move from flat-yield to curve-based discounting. Day count convention mismatch between bond (actual/365.25) and swap (ignores convention field) will produce systematic DV01 errors. |
| **Architect** | New gRPC RPC needed: `CalculateKeyRateDurations`. New table for KRD snapshots. **Deceptive complexity:** curve P&L explain requires rates-service to store historical curve snapshots (verify whether it currently does). |
| **Performance** | **Highest-cost Tier 1 item.** If using VaR-bump framework: 10 tenor buckets × full VaR = 10-20x computation multiplier. **Solution:** compute analytically via `bond_dv01` per tenor, bypassing Monte Carlo entirely. Sub-millisecond. |
| **Data Analyst** | Normalised `daily_krd_snapshots` table (portfolio, instrument, date, tenor_label) as TimescaleDB hypertable. `yield_curve_tenors` has no enforcement that curves contain 2Y/5Y/10Y/30Y points. Linear interpolation for 10Y-30Y gap is imprecise. |
| **Compliance** | **FRTB GIRR requires 12 tenor vertices**, not 4. Decide upfront: internal risk management (4 tenors OK) vs. regulatory GIRR (12 required). Current `sbm.py` uses flat 1.5% risk weight — needs tenor-specific weights per BCBS 352 Table 4. Document this decision in an ADR. |

**Consensus scope:** Start with 4 analytical tenors for internal use. Plan 12-vertex GIRR alignment as a follow-up. Verify rates-service curve history first.

---

#### Item 3 — Expanded Scenario Library

| Perspective | Assessment |
|---|---|
| **Trader** | Agreed Tier 1. But proxy returns in `historical_replay.py` are synthetic 5-day arrays, not actual market data. Must use real returns or per-tenor shocks, not flat asset-class shocks. |
| **Quant** | Parametric shocks applied independently are internally inconsistent — equity -30% and vol +20pp should be correlated, not independent. Need joint scenario specification framework. Current stress engine applies shocks to option premiums directly, ignoring convexity — options must be repriced with shocked underlying. |
| **Architect** | Most straightforward Tier 1 item. Pure Python constants + governance registration. Parametric grid shocks are a UI/orchestration concern — fan out as N sequential gRPC calls. |
| **Product Manager** | Already substantially built (historical replay, custom scenarios, reverse stress, governance). The real gap is "run-all" batch endpoint and comparison table UI. Rename and rescope. |
| **Performance** | Base VaR is recomputed independently per scenario — wasteful. Compute once, share across all 15. Parallelise via `awaitAll` on gRPC calls. Default to parametric for stress. Scales to 50+ scenarios. |
| **QA** | `test_list_scenarios_returns_all_names` asserts `len(names) == 4` — will fail for each addition (intentional). Parametric grid: test that 100% price shock produces stressed_var equal to sum of all long positions' market values. |
| **Compliance** | Must distinguish `REGULATORY_MANDATED` vs. `INTERNAL_APPROVED` scenarios. `computePnlImpact` stub is a documentation risk — persisted results look like economic impact but are not. |

**Consensus scope:** Fix `computePnlImpact` stub first. Add batch "run-all" endpoint. Expand library with real historical return data. Add scenario category field.

---

#### Item 4 — Intraday Risk Timeline

| Perspective | Assessment |
|---|---|
| **Trader** | Intraday P&L timeline is already built (`IntradayPnlChart`, WebSocket, trade annotations). The gap is VaR plotted through the day — nice-to-have, not Tier 1. **Demote to Tier 2.** |
| **Architect** | Partial infrastructure exists. `valuation_jobs` has timestamps and Greeks. 60-second scheduled VaR is the natural sampling rate. Trade annotations require gateway to merge VaR snapshots from risk-orchestrator + trade events from position-service. |
| **UX** | New "Intraday" sub-tab in Risk tab. Dual-axis chart (VaR left, Greeks right). Trade annotations as triangle markers with tooltips. Brush-to-zoom using existing `useBrushSelection` pattern. |
| **QA** | Subtle timing bugs: timezone boundary crossover (23:59 UTC vs 00:01 UTC), annotation placement within 1-second windows, timeline gap detection for calculation outages. |
| **Data Analyst** | Most data-infrastructure-ready item. `intraday_pnl_snapshots` hypertable, `minute_pnl_summary` continuous aggregate, 30-day retention all in place. Use aggregate for chart, raw table only for sub-minute zoom. |
| **Performance** | Low risk. Single-day query touches ~10 TimescaleDB chunks. `minute_pnl_summary` aggregate is the right query target. Do not query raw hypertable for chart display. |

**Consensus scope:** Ready to build today. Primarily a query endpoint + UI chart component. P&L version done; VaR version is the delta.

---

### Tier 2: Do These Next

#### Item 5 — Vol Surface Visualization

| Perspective | Assessment |
|---|---|
| **Trader** | Move to Tier 1 for options desks. The 3D surface is decoration — traders need 2D skew chart and term structure chart. Day-over-day diff is critical for vol trading. |
| **Quant** | Bilinear interpolation on (strike, maturity) is not arbitrage-free. Will produce butterfly arbitrages for skewed surfaces. Error worst for short-dated near-ATM options. Document limitation. |
| **UX** | **3D surface in a browser is almost always a mistake.** Lead with 2D skew chart + term structure chart. Offer 3D as optional mode. Day-over-day diff as dashed-line overlay on skew chart. **Blocker:** no charting library in dependencies — all current charts are hand-rolled SVG. 3D requires Three.js (~600KB) or Plotly (~3MB). New dependency needs approval. |
| **Performance** | **Blocked on library dependency decision.** Cannot be delivered with hand-rolled SVG. Canvas-based WebGL approach required. 240-point surface renders at 60fps with any modern GPU — data volume is not the concern. |
| **Data Analyst** | `volatility_surface_points` has no retention policy — needs one before feature generates high-frequency data. Day-over-day diff requires interpolation to common grid when strike grids differ across days. |

**Consensus scope:** Start with 2D skew + term structure (no new dependency needed — SVG line charts). 3D surface deferred pending library approval. Add retention policy to vol surface tables.

---

#### Item 6 — Multi-Leg Trade Support

| Perspective | Assessment |
|---|---|
| **Trader** | Correctly Tier 2. Do not underestimate risk attribution complexity. Strategy-level P&L explain is substantially harder than single-leg. |
| **Architect** | **Most architecturally invasive item.** No strategy concept in position-service. Needs `trade_strategies` table, strategy-level Greeks aggregation, limit check interactions. Keep risk engine stateless — aggregate in orchestrator. |
| **QA** | **Hardest item to test.** Combinatorial state space: partial fills, concurrent leg amendments, expiry of one leg. Needs controlled concurrency tests. No current test seeders create multi-leg fixtures. |
| **Product Manager** | **Highest scope creep risk.** Every conversation surfaces new edge cases (margin treatment, corporate actions, leg expiry lifecycle). Scope v1 as "strategy-level Greeks aggregation for display only." **Requires design spike before sprint planning.** |
| **Performance** | No performance concerns at rest. Strategy Greeks aggregation is O(n_legs) arithmetic. |

**Consensus: Move to Tier 3 with mandatory design spike first.**

---

#### Item 7 — Custom Report Builder

| Perspective | Assessment |
|---|---|
| **Trader** | Underprioritize. Report builders serve middle-office, not traders. CSV export already built. **Move to Tier 3.** |
| **Performance** | **Most operationally dangerous item** from stability perspective. Arbitrary queries can sequential-scan multi-year hypertables. Requires: statement_timeout, row count guard, restricted schema access, query cost estimation, no raw SQL. |
| **Data Analyst** | Right approach: create a `risk_positions_flat` materialised view (denormalised join of positions + Greeks + VaR contribution). Covers 80% of use cases without query federation. |
| **Security** | **Data exfiltration risk.** Users could craft reports spanning books they shouldn't see. Requires server-side book-ownership enforcement and field-level sensitivity classification. Must audit every report generation. |
| **Product Manager** | Gate on specific client commitment. Maximum 3 template types in v1. Do not build Tableau in a trading system. |

**Consensus: Move to Tier 3. Gate on client commitment.**

---

#### Item 8 — RBAC

| Perspective | Assessment |
|---|---|
| **Security** | **STOP-SHIP.** All `devModule()` routes are unauthenticated. JWT infrastructure exists but is not wired in the default deployment. WebSocket routes have no auth on connection upgrade. Book-level ownership checks (BOLA prevention) do not exist. `approvedBy`/`createdBy` fields are caller-supplied strings, not extracted from JWT principal. |
| **Compliance** | Model governance self-approval not blocked. Temporary limit increase self-approval not blocked. Audit read access not restricted from TRADER role. Need formal SoD matrix. |
| **Architect** | Infrastructure fully built (ADR-0013, Keycloak, 5 roles, 12 permissions). What's missing is consistent enforcement. One sprint to audit routes and add `requirePermission()` wrappers. |
| **Product Manager** | Decompose into: (a) wire auth globally, (b) verify book-level scoping, (c) add role-gated UI states. Weeks, not quarters. |
| **QA** | Missing tests: privilege escalation (TRADER accessing COMPLIANCE endpoints), empty roles JWT, multi-role conflict, token expiry during long-running requests. No Playwright tests verify role-based UI states. |

**Consensus: Move to Tier 0 — prerequisite for all other work.**

---

### Tier 3: Longer Horizon

#### Item 9 — OMS Integration

| Perspective | Assessment |
|---|---|
| **Trader** | **Miscategorised.** FIX infrastructure is already substantially built: `FIXOrderSender`, `FIXExecutionReportProcessor`, `OrderSubmissionService`, `PrimeBrokerReconciliation`. Remaining work is wiring and hardening, not new capability. |
| **Security** | Attack surface concerns: FIX tag injection via `parseTags()`, session identity spoofing, `ExecType=5` replace attack, `POST /api/v1/orders` has no auth. `GET /api/v1/fix/sessions` exposes sequence numbers — ADMIN only. FIX credentials need K8s Secrets from vault, not values files. |
| **QA** | Needs FIX session simulator for integration tests (does not exist). Critical edge cases: duplicate fill detection, sequence number gaps, partial fill reconciliation. |

**Consensus: Reclassify as Tier 2 completion/hardening item.**

---

#### Item 10 — Benchmark/Performance Attribution

| Perspective | Assessment |
|---|---|
| **Trader** | Correctly Tier 3. Brinson is equity-centric — irrelevant for rates/FX/commodity desks. Factor P&L attribution already covers multi-asset needs. |
| **Quant** | Recommend geometric multi-period attribution (Menchero framework) — zero residual by construction. Requires benchmark reference data layer that does not exist. |
| **Architect** | None of the existing services handle benchmark data. Needs `benchmarks` table, historical portfolio weight snapshots, and the Brinson calculation module. Openly complex. |
| **Product Manager** | Only relevant for long-only equity asset manager clients. Gate on specific client type. |

**Consensus: Correctly Tier 3. Gate on client need.**

---

#### Item 11 — What-If for Complex Rebalancing

**Consensus: Correctly Tier 3.** Simple what-if already built. Complex version (rolling options, curve trades) requires simulation framework and depends on vol surface (Item 5) and liquidity layer being complete.

---

#### Item 12 — Regulatory Expansion (SA-CCR, CVA Capital, Large Exposures)

| Perspective | Assessment |
|---|---|
| **Compliance** | **Elevate SA-CCR to Tier 2** if platform is used for regulatory capital. CRR3/PRA PS17/23 timelines are in force. Existing PFE (Monte Carlo GBM) is an internal economic model — it is not SA-CCR. The two coexist; you cannot substitute one for the other. CVA capital charge (BCBS 325 SA-CVA) is a separate exercise. Large exposure reporting (CRR Article 394) requires SA-CCR as prerequisite. |
| **Quant** | SA-CCR is computationally simple — table lookup and aggregation, not simulation. Needs: supervisory delta mapper, hedging set aggregator, AddOn per asset class, RC calculation. Self-contained module using existing BS pricing primitives. |
| **Architect** | Substantial new schema (trade classification tables). Multi-month effort. Correctness requires regulatory validation. |

**Consensus: Elevate SA-CCR to Tier 2 if in regulatory scope. Document scope boundary explicitly.**

---

## Missing Items Not On The Roadmap

Several reviewers identified critical gaps absent from Marcus's proposal:

### 1. `computePnlImpact` Stub Fix (Trader, Product Manager, Compliance)
`StressScenarioService.computePnlImpact()` sums raw JSON shock values instead of computing shock x position market value. **This is an active bug** producing fiction in the scenario P&L numbers. Must be fixed before any scenario work ships. Convert from code TODO to tracked model limitation in governance framework.

### 2. Static FX Rate Provider (Trader)
`StaticFxRateProvider` uses hardcoded rates. If EUR/USD moves 1.5% intraday (normal on ECB days), multi-currency P&L is wrong. `LiveFxRateProvider` exists but FX rate database persistence is incomplete.

### 3. Settlement and Cash Flow Risk (Trader)
No concept of cash flows, settlement dates, or funding costs. Bond coupons, swap resets, and maturity proceeds are invisible. P&L for fixed income is systematically incomplete without this.

### 4. Alert Acknowledgement Workflow (Product Manager)
Alerts fire with severity levels but there is no acknowledge/own/escalate workflow. Compliance cannot distinguish "dismissed" from "ignored" limit breaches.

### 5. Audit Coverage Expansion (Security, Compliance)
Audit trail captures only trade lifecycle events. Missing: risk calculation events, model governance transitions, limit breaches, scenario executions, RBAC denials. `eventType` taxonomy needs expansion. All governance events should flow through the hash-chained audit trail.

### 6. Model Governance Hardening (Compliance)
`ModelRegistry.transitionStatus()` does not enforce four-eyes (self-approval not blocked). `ModelVersion` lacks fields for: model tier, validation report reference, known limitations, approved use cases, next validation date. `validationReportUrl` field needed.

---

## Revised Priority Sequencing

Based on consensus across all 10 reviewers:

### Phase 0: Fix First (Bugs and Security — No Sprint Required)
- [ ] Wire `authenticate("auth-jwt")` around all `devModule()` routes
- [ ] Extract `approvedBy`/`createdBy` from JWT principal, not request body
- [ ] Authenticate WebSocket connection upgrades
- [ ] Fix `computePnlImpact` stub to call risk engine
- [ ] Fix `volChange`/`rateChange` zero-fill in `buildAttributionInputs()`
- [ ] Add book-level ownership checks (BOLA prevention)
- [ ] Block model governance self-approval in `ModelRegistry`

### Phase 1: High-Value Gaps (Next Quarter)
1. **Key rate durations** — 4-tenor analytical DV01 bucketing (no Monte Carlo). Verify rates-service curve history first. ADR for 4-tenor vs. 12-tenor GIRR decision.
2. **P&L explain cross-gamma** — Add cross-gamma term using analytical BS Greeks (not VaR sensitivities). Register as model in governance. Rebuild `daily_pnl_summary` continuous aggregate.
3. **Expanded scenario library** — Batch "run-all" endpoint + comparison table. Replace synthetic proxy returns with real historical data. Add `scenarioCategory` field. Joint scenario specification (correlated shocks).
4. **Intraday VaR timeline** — VaR plotted through day with trade annotations. Query endpoint + UI chart on existing infrastructure.
5. **Vol surface visualization (2D)** — Skew chart + term structure chart in SVG (no new dependency). Day-over-day diff overlay. Add retention policy to vol surface tables.

### Phase 2: Differentiation (Two to Four Quarters)
6. **SA-CCR** — If in regulatory capital scope. Replacement Cost + PFE AddOn using supervisory factors.
7. **OMS/FIX hardening** — Wire real FIX connectivity on existing skeleton. Security hardening per review findings.
8. **Audit coverage expansion** — Expand event taxonomy. Route governance events through hash-chained trail.
9. **Alert acknowledgement workflow** — Acknowledge/own/escalate with audit records.
10. **FRTB GIRR upgrade** — 12-vertex tenor bucketing with prescribed risk weights and correlations.

### Phase 3: Design Spike Required
11. **Multi-leg trade support** — Design document before estimation. Start with display-only strategy Greeks.
12. **Custom report builder** — Gate on client commitment. Start with `risk_positions_flat` materialised view.
13. **Benchmark attribution** — Gate on long-only equity client need.
14. **Complex rebalancing what-if** — Depends on vol surface and liquidity layer.

---

## Cross-Cutting Concerns

### Performance Budget
- KRD must use analytical DV01, not VaR bumps (10-20x cost multiplier otherwise)
- Scenario batch must share base VaR across all scenarios and parallelise stressed evaluations
- Custom report builder needs statement_timeout, row count guard, restricted schema access
- Greek calculation suite: ~33 VaR calls per book per cycle. At 50ms gRPC round-trip × 10 books = 16.5 seconds. Profile before enabling real-time cross-Greeks.

### Data Quality Prerequisites
- SOD Greek snapshot table (immutable, locked before market open) needed for P&L explain
- Yield curves must contain standard tenor points — no enforcement exists today
- Historical scenario returns must come from real market data, not synthetic arrays
- `stress_scenarios.shocks` TEXT column should migrate to JSONB with schema validation

### Regulatory Obligations Not Yet Addressed
- COREP XBRL reporting format for FRTB submissions
- MiFID II RTS 22 fields for multi-leg transaction reporting
- BCBS 239 formal gap assessment
- Stress test results need 7-year retention policy (currently none)
- Backtest results retention should extend from 3 years to 7 years

### UX Principles for New Features
- Risk tab dashboard is already 11+ content sections — needs progressive disclosure before adding KRD and intraday timeline
- Vol surface: 2D before 3D. No new charting library without approval.
- Maintain existing patterns: `Card` component, sub-tab underline pattern, slide-in panel pattern, dark SVG chart palette, `font-mono tabular-nums` on all numeric values
- Extract shared `FACTOR_COLORS` constant (currently duplicated in `PnlWaterfallChart` and `PnlAttributionTable`)

### Infrastructure Prerequisites (SRE)

The SRE review identified foundational infrastructure issues that must be addressed before Tier 1 features land:

**Fix before any Tier 1 work:**
- [ ] **Redis `maxmemory-policy allkeys-lru`** — currently unconfigured. Redis OOM errors take down the entire cache layer. One-line fix.
- [ ] **Distributed locks on scheduled jobs** — all risk-orchestrator scheduled jobs (`ScheduledVaRCalculator`, `ScheduledCrossBookVaRCalculator`, etc.) run as coroutine loops with no distributed lock. Safe only because `replicaCount: 1`. The moment autoscaling is enabled, two schedulers run in parallel, doubling DB writes and corrupting the Redis VaR cache. Add Redis SETNX locks.
- [ ] **Postgres exporter in observability stack** — TimescaleDB compression failures, continuous aggregate lag, and connection pool exhaustion are currently invisible. No Prometheus metrics are emitted from Postgres.
- [ ] **Kafka replication factor** — single-broker KRaft with `REPLICATION_FACTOR=1` in dev. Verify RF=3 on any non-dev environment. Single broker failure = total loss of all 16 topics.
- [ ] **Write V35-rollback.sql** — `intraday_pnl_snapshots` hypertable has no rollback script (unlike V19, V21, V22, V27). Once data is written, there is no clean rollback without data loss.
- [ ] **Raise risk-orchestrator JVM heap** — currently `-Xmx256m`. Full P&L explain adds vol surface and yield curve fetches per book per cycle. Raise to `-Xmx512m -Xms128m`.

**Fix before FIX/OMS goes live (Tier 3, Item 9):**
- [ ] **Postgres HA** — single Postgres on the order execution path is unacceptable. FIX session recovery depends on `fix_sessions` table availability. Need hot standby with streaming replication at minimum.
- [ ] **Fixed outbound IP for FIX** — brokers firewall-whitelist source IPs. Pod IP churn breaks FIX sessions on every deployment. Need NAT Gateway with static EIP on EKS.
- [ ] **FIX session down runbook** — sequence number reset procedure, in-flight order reconciliation, broker coordination steps. Must exist before go-live.
- [ ] **`PrimeBrokerReconciliationService` trigger on reconnect** — currently scheduled daily only. Must also run on every FIX session reconnect to catch fills during outage.

**Monitoring gaps for new features:**
- `pnl_unexplained_fraction{book_id}` alert when >5% for two consecutive calculations (Item 1)
- `krd_change_fraction{book_id, tenor}` alert on >20% intraday change (Item 2)
- `timescaledb_continuous_aggregate_jobs_failed_total` alert (requires Postgres exporter) (Item 4)
- Vol surface staleness distinct from price staleness in `MarketDataStale` alert (Item 5)
- `fix_session_status{session_id, counterparty}` gauge — severity-critical on drop during market hours (Item 9)

**Deployment sequencing constraints:**
- Items 3, 5, 7, 10 can deploy independently (additive only, no schema changes)
- Items 1, 2, 4, 6 require migration-before-code coordinated deploys
- Item 8 (RBAC) must deploy in order: Keycloak realm update → gateway enforcement → downstream services
- Item 9 (FIX) requires 4-6 weeks broker onboarding lead time (IP whitelisting, FIXNET circuit, connectivity testing)

**Rollback risk classification:**
- Safe: Items 3, 5, 7, 10 (feature-flaggable at gateway/UI)
- Requires care: Items 1, 4, 6, 8 (data written in new schema; rollback window before production data accumulates)
- Cannot roll back unilaterally: Item 9 (broker has records of open positions)

---

*Generated by review team on 25 March 2026. Each specialist independently explored the codebase before contributing findings.*
