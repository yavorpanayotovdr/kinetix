# Kinetix Platform -- Comprehensive System Improvement Plan

**Date:** 2026-03-01
**Reviewed by:** Quant, UX Designer, Data Analyst, Compliance Officer, Trader, Architect
**Status:** Draft -- Awaiting Approval

---

## Executive Summary

Six specialist agents conducted an independent, code-level review of the entire Kinetix platform. The system has **strong fundamentals**: clean service decomposition, comprehensive testing, solid event-driven architecture, and a rich risk analytics engine. However, significant gaps exist across **derivative pricing** (no options models), **trade lifecycle** (no amend/cancel), **regulatory governance** (non-tamperproof audit trail), and **data operations** (no DLQ, no retention policies).

This plan organises all findings into 6 phases of work, prioritised by business impact and risk.

---

## Current System Maturity Assessment

| Domain | Maturity | Key Strength | Critical Gap |
|--------|----------|-------------|-------------|
| Architecture | 4/5 | Clean service boundaries, strong testing | Risk-orchestrator coupled to position-service DB |
| Risk Models | 3/5 | Three VaR methods, FRTB SA coverage | No options pricing, Greeks are VaR-based not pricing-based |
| Trading Workflow | 2/5 | Real-time risk, What-If analysis | No trade amend/cancel, no blotter, no pre-trade limits |
| Compliance | 3/5 | FRTB 3-pillar coverage, RBAC | Non-immutable audit trail, no model governance |
| Data | 3/5 | Good schemas, TimescaleDB, Kafka | No DLQ, no retention, no data quality monitoring |
| UI/UX | 3.5/5 | Strong data viz, progressive disclosure | No accessibility, no dark mode, no customisation |

---

## Phase 1: Critical Fixes (Weeks 1-2)

*Items that are correctness issues or would be flagged in any security/regulatory review.*

### 1.1 Fix Rho Greek Implementation
**Source:** Quant
**Problem:** Rho bumps all volatilities by 1bp instead of bumping the risk-free rate. This is not rate sensitivity -- it's a second vega.
**Action:** Change `greeks.py` to bump the discount rate/yield curve, not volatilities.
**Effort:** Small

### 1.2 Make VaR Deterministic by Default
**Source:** Quant
**Problem:** Default `VolatilityProvider.with_jitter()` adds random noise, making consecutive VaR calculations on identical positions produce different results.
**Action:** Use `VolatilityProvider.static()` as the default. Jitter should be opt-in.
**Effort:** Small

### 1.3 Hash-Chained Audit Trail
**Source:** Compliance
**Problem:** Audit events have no immutability enforcement. No DB triggers prevent UPDATE/DELETE, no cryptographic hash chain.
**Action:**
- Add `previous_hash` and `record_hash` columns to `audit_events`
- Implement SHA-256 hash chain: `hash(previous_hash + record_data)`
- Add DB-level rules: `CREATE RULE audit_no_update AS ON UPDATE TO audit_events DO INSTEAD NOTHING`
- Add verification API endpoint
**Effort:** Medium

### 1.4 Extend Audit Coverage
**Source:** Compliance
**Problem:** Audit only captures trade events. Missing: risk calculations, auth events, config changes, report generation, alert rule modifications.
**Action:** Add `userId`, `userRole`, and `eventType` fields to AuditEvent. Publish audit events from all services for key actions.
**Effort:** Medium

### 1.5 Decouple Risk-Orchestrator from Position-Service Database
**Source:** Architect
**Problem:** `risk-orchestrator` has `implementation(project(":position-service"))` and directly accesses position-service's database via `ExposedPositionRepository`. Violates database-per-service principle.
**Action:** Create a `PositionServiceHttpClient` in risk-orchestrator that calls position-service's REST API. Remove the Gradle dependency.
**Effort:** Medium

### 1.6 Kafka Dead Letter Queues and Retry Logic
**Source:** Data Analyst
**Problem:** All Kafka consumers silently log and skip failed messages. No retry, no DLQ, no idempotency.
**Action:**
- Create `*.dlq` topics for each consumer
- Implement 3-retry with exponential backoff before routing to DLQ
- Add idempotency checks (`INSERT ... ON CONFLICT DO NOTHING` for audit; job deduplication for risk)
- Add alerting on DLQ accumulation
**Effort:** Medium

---

## Phase 2: Trading Workflow Essentials (Weeks 3-5)

*Features that any working trader would expect on day one.*

### 2.1 Trade Amend and Cancel
**Source:** Trader
**Problem:** Once booked, trades cannot be amended or cancelled. No trade status field.
**Action:**
- Add `type` field to TradeEvent: `NEW`, `AMEND`, `CANCEL`
- Add `status` field to Position: `LIVE`, `AMENDED`, `CANCELLED`
- Add `PUT /portfolios/{id}/trades/{tradeId}` and `DELETE /portfolios/{id}/trades/{tradeId}` endpoints
- Update position calculations to handle amendments and cancellations
**Effort:** Large

### 2.2 Trade Blotter UI
**Source:** Trader
**Problem:** Backend has `TradeEventRepository.findByPortfolioId()` but no UI to display trade history.
**Action:** Build a TradeBlotter component with columns: time, instrument, side, quantity, price, status. Add date range filter, instrument filter, side filter, and CSV export.
**Effort:** Medium

### 2.3 Pre-Trade Limit Checks
**Source:** Trader + Compliance
**Problem:** No pre-trade limit validation. Alerts are post-trade only.
**Action:**
- Add `LimitCheckService` in gateway or position-service
- Soft limits (warn) and hard limits (block)
- Check position limits, notional limits, concentration limits before persisting
- Return limit breach details in the trade response
**Effort:** Medium

### 2.4 Realized P&L Tracking
**Source:** Trader
**Problem:** Only unrealized P&L tracked. Closing a position doesn't record realized gains.
**Action:** Modify `Position.applyTrade()` to compute realized P&L when reducing/closing. Add `realized_pnl` column to positions table. Show in P&L tab.
**Effort:** Medium

### 2.5 Multi-Currency Portfolio Aggregation
**Source:** Trader
**Problem:** No cross-currency aggregation. A portfolio with EUR and USD positions has no unified NAV.
**Action:** Add base currency per portfolio configuration. Integrate FX rates from rates-service for conversion. Show unified NAV in portfolio summary.
**Effort:** Medium

---

## Phase 3: Quantitative Models (Weeks 5-8)

*Fill the core pricing and risk model gaps.*

### 3.1 Black-Scholes Options Pricing
**Source:** Quant
**Problem:** No options pricing model. Derivatives are treated as simple market-value positions.
**Action:**
- Extend `Position` proto with option fields: `strike`, `expiry`, `option_type`, `underlying_id`
- Implement Black-Scholes/Black-76 pricing engine
- Compute proper pricing Greeks (delta, gamma, vega, theta, rho) from the pricing model
- Keep existing VaR-Greeks as a separate "risk sensitivity" view
**Effort:** Large

### 3.2 Wire Up Volatility Surface and Yield Curve Data
**Source:** Quant
**Problem:** Market data services fetch vol surfaces and yield curves, but `market_data_consumer.py` doesn't process them.
**Action:**
- Add `VOLATILITY_SURFACE` and `YIELD_CURVE` processing to `market_data_consumer.py`
- Implement bilinear interpolation for vol surfaces (strike x maturity)
- Implement log-linear interpolation on discount factors for yield curves
**Effort:** Medium

### 3.3 Fix Historical VaR
**Source:** Quant
**Problem:** "Historical" VaR generates random returns via Cholesky -- it's actually another Monte Carlo. True historical VaR should replay actual historical returns.
**Action:** Use `HISTORICAL_PRICES` data (already fetched) to compute actual historical returns for scenario replay.
**Effort:** Medium

### 3.4 EWMA / GARCH Volatility Estimation
**Source:** Quant
**Problem:** Parametric VaR uses static volatilities with no decay factor. Standard practice is EWMA with lambda ~0.94.
**Action:** Implement EWMA with configurable decay factor. Optionally integrate LSTM vol predictions into VaR pipeline.
**Effort:** Medium

### 3.5 VaR Backtesting Framework
**Source:** Quant + Compliance
**Problem:** No systematic backtesting. Required by regulators (Basel green/yellow/red zones).
**Action:**
- Store daily VaR predictions and compare against realized P&L
- Implement Kupiec POF test and Christoffersen independence test
- Traffic light system (green/yellow/red) per Basel requirements
- Show backtesting results in regulatory tab
**Effort:** Medium

### 3.6 Enhanced DRC with Credit Quality Bucketing
**Source:** Quant + Compliance
**Problem:** Only 3 rating buckets with fixed default probabilities. CRR3 requires 15+ credit quality steps.
**Action:** Add granular credit rating integration, maturity-based JTD, seniority-based LGD differentiation.
**Effort:** Medium

---

## Phase 4: Data Hardening (Weeks 6-9)

*Make the data layer production-ready.*

### 4.1 TimescaleDB Retention and Compression Policies
**Source:** Data Analyst
**Problem:** No retention or compression policies configured despite TimescaleDB being in use.
**Action:**
- `prices`: 2-year raw, 5-year compressed
- `valuation_jobs`: 1-year detailed, archive summaries
- `audit_events`: 7-year regulatory retention
- Add compression policies for data older than 30 days
**Effort:** Small

### 4.2 Continuous Aggregates
**Source:** Data Analyst
**Problem:** All queries run against raw data. No pre-computed rollups.
**Action:** Create hourly VaR averages, daily P&L summaries, weekly risk trend continuous aggregates.
**Effort:** Medium

### 4.3 Redis for Shared Caching
**Source:** Data Analyst
**Problem:** `LatestVaRCache` is an in-memory ConcurrentHashMap. Lost on restart. Not shared across instances.
**Action:** Replace with Redis (already in infrastructure stack) with appropriate TTLs.
**Effort:** Small

### 4.4 Correlation ID Propagation
**Source:** Data Analyst + Architect
**Problem:** No traceability across event pipeline. Can't trace which price event triggered which VaR calculation.
**Action:** Add `correlationId` to all Kafka events and propagate through to ValuationJob steps.
**Effort:** Medium

### 4.5 Provision All Kafka Topics Explicitly
**Source:** Data Analyst
**Problem:** Only 3 of 11+ topics are created in `create-topics.sh`. Rest rely on auto-creation.
**Action:** Update script to include all topics with appropriate partition counts.
**Effort:** Small

### 4.6 Fix Audit Schema Types
**Source:** Data Analyst
**Problem:** `quantity`, `price_amount`, `traded_at` stored as VARCHAR in audit_events.
**Action:** Migrate to NUMERIC(28,12) and TIMESTAMPTZ for type-safe queries.
**Effort:** Small

### 4.7 Add Missing Database Indexes
**Source:** Data Analyst
**Problem:** Missing indexes on `positions(instrument_id)`, `valuation_jobs(status)`, `daily_risk_snapshots(snapshot_date)`, `frtb_calculations(portfolio_id, calculated_at)`.
**Action:** Add indexes via Flyway migrations.
**Effort:** Small

---

## Phase 5: UI/UX Improvements (Weeks 7-10)

*Bring the UI to institutional standard.*

### 5.1 Accessibility (WAI-ARIA)
**Source:** UX Designer
**Problem:** No ARIA roles on tab bar, no aria-labels on data displays, no keyboard navigation.
**Action:**
- Add `role="tablist"`, `role="tab"`, `aria-selected` to tab bar
- Add keyboard arrow-key navigation between tabs
- Add `aria-label` to VaR values, ES, P&L, Greeks
- Add `aria-live="polite"` to real-time data updates
**Effort:** Medium

### 5.2 WebSocket Auto-Reconnect
**Source:** UX Designer
**Problem:** No reconnection logic. Disconnection requires manual page refresh.
**Action:** Implement exponential backoff reconnection (1s, 2s, 4s, 8s... up to 30s). Show "Reconnecting..." state.
**Effort:** Small

### 5.3 Position Grid Pagination/Virtualisation
**Source:** UX Designer
**Problem:** All positions rendered in a single table. Performance degrades with hundreds of instruments.
**Action:** Add pagination (50 rows per page) or virtual scrolling via `@tanstack/virtual`.
**Effort:** Medium

### 5.4 Column Visibility Toggles
**Source:** UX Designer
**Problem:** No ability to customise which columns are shown in the position grid.
**Action:** Add settings dropdown to show/hide columns. Persist preferences in localStorage.
**Effort:** Small

### 5.5 Consolidate Currency Formatting
**Source:** UX Designer
**Problem:** Three duplicate `formatCurrency()` functions producing inconsistent output.
**Action:** Extract into shared `utils/format.ts`. Use consistently across all components.
**Effort:** Small

### 5.6 Dark Mode
**Source:** UX Designer
**Problem:** Light theme only. Financial trading desks predominantly use dark themes.
**Action:** Implement dark mode with Tailwind `dark:` variants. Store preference in localStorage.
**Effort:** Medium

### 5.7 Confirmation on Alert Rule Deletion
**Source:** UX Designer
**Problem:** Delete button on alert rules has no confirmation dialog.
**Action:** Reuse existing `ConfirmDialog` component.
**Effort:** Small

### 5.8 CSV Export from All Tabs
**Source:** UX Designer
**Problem:** Export only available from regulatory tab.
**Action:** Add CSV export to positions, risk, P&L, and alerts tabs.
**Effort:** Small

---

## Phase 6: Advanced Capabilities (Weeks 10+)

*Features that differentiate the platform.*

### 6.1 Cross-Greeks (Vanna, Volga, Charm)
**Source:** Quant
**Problem:** No second-order cross-Greeks for derivatives hedging.
**Action:** Implement as numerical cross-derivatives of the pricing function (requires Phase 3.1 first).
**Effort:** Medium

### 6.2 Variance Reduction for Monte Carlo
**Source:** Quant
**Problem:** No antithetic variates, control variates, or importance sampling.
**Action:** Implement antithetic variates as a first step (doubles efficiency with minimal code).
**Effort:** Small

### 6.3 Dynamic Correlation Estimation
**Source:** Quant
**Problem:** Static/hardcoded correlation matrix. No historical estimation.
**Action:** Implement Ledoit-Wolf shrinkage estimator from historical returns.
**Effort:** Medium

### 6.4 Model Governance Framework
**Source:** Compliance
**Problem:** No model validation, versioning, or change management framework.
**Action:**
- Add model_version tracking to FRTB calculations and VaR jobs
- Version risk_weights.py parameters separately from code
- Build backtesting infrastructure (Phase 3.5 feeds into this)
- Add model change approval workflow
**Effort:** Large

### 6.5 Regulatory Submission Workflow
**Source:** Compliance
**Problem:** Reports generated but no submission tracking, no four-eyes approval.
**Action:** Build submission tracker, approval workflow, deadline management, receipt confirmation.
**Effort:** Large

### 6.6 Limit Management Hierarchy
**Source:** Compliance + Trader
**Problem:** No formal limit hierarchy (desk/firm/counterparty), no intraday vs overnight limits.
**Action:** Build limit management service with hierarchical limits and temporary limit increase approvals.
**Effort:** Large

### 6.7 Margin Calculator
**Source:** Trader
**Problem:** No IM/VM calculation for margin impact analysis.
**Action:** Add SPAN/SIMM margin estimation to the What-If panel.
**Effort:** Large

### 6.8 Counterparty Risk View
**Source:** Trader
**Problem:** No counterparty field on trades, no credit exposure aggregation.
**Action:** Add counterparty to Trade model, build exposure aggregation and credit limit monitoring.
**Effort:** Large

### 6.9 Multi-Portfolio Aggregate View
**Source:** Trader
**Problem:** Portfolio selector shows one portfolio at a time. No desk-level aggregation.
**Action:** Add "All Books" option to portfolio selector that aggregates risk across portfolios.
**Effort:** Medium

### 6.10 Stress Testing Governance
**Source:** Compliance
**Problem:** Scenarios hardcoded. No approval workflow, no reverse stress testing, no EBA scenarios.
**Action:** Allow configurable scenarios via UI/API, add scenario approval workflow, implement reverse stress testing.
**Effort:** Medium

### 6.11 Data Quality Monitoring
**Source:** Data Analyst
**Problem:** No reconciliation checks, no staleness detection, no completeness monitoring.
**Action:** Add scheduled checks for price staleness, SOD baseline completeness, and position-risk reconciliation.
**Effort:** Medium

### 6.12 Workspace Customisation
**Source:** UX Designer
**Problem:** No saved views, pinned scenarios, default time ranges, or layout persistence.
**Action:** Allow users to save workspace configurations. Persist in localStorage or user preferences API.
**Effort:** Medium

---

## Architect-Specific Refactoring Items

These items from the architect review should be addressed opportunistically alongside the phased work above:

| Item | Priority | Phase |
|------|----------|-------|
| Extract shared event models (PriceEvent, TradeEvent) to common module | Medium | Phase 1 |
| Move DTOs out of route files into `dtos` sub-packages | Low | Ongoing |
| Add CORS configuration to gateway | Medium | Phase 2 |
| Introduce Koin DI or refactor manual wiring in Application.kt files | Medium | Phase 4 |
| Add API versioning strategy documentation | Low | Phase 6 |
| Refactor gateway module overloads | Low | Ongoing |
| Externalise connection pool configs from code to application.conf | Low | Phase 4 |

---

## Summary of Items by Priority

| Priority | Count | Key Themes |
|----------|-------|-----------|
| **Critical / Phase 1** | 6 | Correctness fixes, audit integrity, decoupling |
| **High / Phase 2** | 5 | Trade lifecycle, pre-trade controls, realized P&L |
| **High / Phase 3** | 6 | Options pricing, market data wiring, backtesting |
| **Medium / Phase 4** | 7 | Data ops, retention, caching, traceability |
| **Medium / Phase 5** | 8 | Accessibility, dark mode, UX polish |
| **Lower / Phase 6** | 12 | Advanced models, governance, margin, customisation |
| **Total** | **44** | |

---

## Cross-Cutting Concerns Identified by Multiple Agents

These items were flagged independently by 2+ agents, indicating strong consensus:

1. **Pre-trade limit checks** -- Trader + Compliance
2. **VaR backtesting** -- Quant + Compliance
3. **DRC enhancement** -- Quant + Compliance
4. **Kafka DLQ / retry** -- Data + Architect
5. **Correlation ID propagation** -- Data + Architect
6. **Audit completeness** -- Compliance + Architect
7. **SBM sensitivity accuracy** -- Quant + Compliance
8. **Data retention policies** -- Data + Compliance
9. **Model governance** -- Quant + Compliance
10. **Stress testing governance** -- Quant + Compliance

---

*This plan was generated from independent reviews by 6 specialist agents. Each agent read actual source code and formed opinions based on what exists in the codebase, not assumptions.*
