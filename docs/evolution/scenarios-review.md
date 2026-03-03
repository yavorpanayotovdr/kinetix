# Scenarios Tab Review — Trader Feedback

**Reviewer:** Marcus (Senior Trader, 25+ years sell-side/buy-side experience)
**Date:** 2026-03-03

---

## Current State

The tab has basic plumbing: pick a scenario from a dropdown, hit run, see a result. Asset-class breakdown bars are a nice touch. Results accumulate across runs. Summary card in the Risk tab showing top 3 by P&L impact is good instinct.

**Verdict:** This is a toy. It answers "what would happen to my VaR under GFC conditions?" and nothing else. That's ~10% of what a real trading desk needs.

---

## Priority 1: Must-Have

### 1. Run All Scenarios at Once

Give me a "Run All" button. Display all scenarios in a comparison table — all scenarios as rows, columns for Base VaR, Stressed VaR, P&L Impact, and VaR multiplier (Stressed/Base). Sort by worst P&L impact descending. This is the *primary* view of this tab.

**Why:** During COVID in March 2020, I was running stress tests every 30 minutes. Clicking through scenarios one at a time is not viable when the market is moving 5% a day.

### 2. Position-Level Drill-Down

When GFC shows a $2M P&L hit on equities, *which positions* are driving that? Need a table showing: instrument, base market value, stressed market value, position P&L impact, and percentage contribution to total stress loss. Clickable from the asset class bar — click "EQUITY" and see the positions underneath.

**Why:** The whole point of stress testing is to identify *what to cut*. A stress test without position drill-down is just a scary number you can't act on. During the Euro crisis, we had to liquidate specific sovereign bond positions within hours.

### 3. Limit Breach Highlighting Under Stress

Show which risk limits would be breached under each scenario. Run the stress scenario and flag: "Under GFC_2008, your equity concentration would hit 78% against a 60% limit." Color-code rows — green (within limits), amber (>80% of limit), red (breach).

**Why:** The first question the head of risk asks after any stress test is "are we in breach of anything?" The system should just tell me.

### 4. Custom Scenario Builder

The backend already supports custom vol and price shocks per asset class — the UI doesn't expose it. Provide a form where the user can:
- Name the scenario
- Set vol shock multipliers per asset class (sliders or number inputs, 0.5x to 5.0x)
- Set price shock multipliers per asset class (0.5 to 1.5, where <1 is a selloff)
- Optionally set confidence level and time horizon
- Save it (via the regulatory-service's scenario management) or run it ad-hoc

**Why:** Historical scenarios are backward-looking. The next crisis won't look like 2008 or 2020. Every real trading desk builds custom scenarios on top of historical ones. Questions like "what if equities drop 15% and vol doubles, but rates are flat?" require this.

---

## Priority 2: Important

### 5. Scenario Comparison View

Side-by-side comparison of 2-3 scenarios. Same layout, same metrics, next to each other. Asset class impact bars overlaid or side-by-side so you can visually see "GFC hits equities hardest, Euro Crisis hits fixed income hardest."

**Why:** Different scenarios stress different parts of the book. Understanding *which* crisis is the worst case and *why* is essential for hedging decisions.

### 6. Scenario Governance Integration

The regulatory service has a full approval workflow (DRAFT -> PENDING_APPROVAL -> APPROVED -> RETIRED) with four-eyes principle but zero UI. The Scenarios tab should show:
- Which scenarios are approved (badge/icon)
- A section to manage scenarios (create, submit for approval, view pending)
- Only approved scenarios available for official stress reports
- Draft/ad-hoc scenarios clearly labelled

**Why:** Regulators (Basel FRTB, internal audit) require documented, approved stress scenarios. The governance trail is half the point.

### 7. Export Results

CSV and PDF export of full stress test results, including position-level detail. The PDF should be formatted as a report — date, portfolio, scenarios run, summary table, asset class breakdown, position detail.

**Why:** Stress test results that live only on a screen are useless for audit and reporting. Every morning briefing includes a printed stress test summary.

### 8. Stressed Greeks Display

When stressing the portfolio, show stressed Greeks alongside stressed VaR. Specifically: how does delta, gamma, and vega change under the stress scenario? If gamma flips sign under stress, that's critical information.

**Why:** VaR tells you how much you could lose. Stressed Greeks tell you how your *risk profile* changes — which is what you need for hedging. If vega doubles under a vol shock scenario, you need vol protection.

---

## Priority 3: Nice-to-Have

### 9. Reverse Stress Testing

Instead of "what happens under scenario X?", answer "what scenario would cause a loss of $Y?" Input a P&L threshold (e.g., -$5M) and show what combination of shocks gets there.

**Why:** Basel and PRA require reverse stress testing. It answers "how bad would things have to get before I'm really in trouble?"

### 10. Historical Stress Test Time Series

Store stress test results over time. Chart: "GFC stress P&L impact over the last 30 days." If the number is getting worse, the book is becoming more vulnerable.

**Why:** Trend analysis on stress results catches a portfolio drifting into a dangerous regime. If GFC stress loss goes from $1M to $3M over two weeks, something in the book needs attention.

### 11. What-If + Stress Combo

Combine the existing What-If analysis with stress testing: "if I add 500 shares of AAPL, what does my stress profile look like?" Run scenarios against the hypothetical portfolio.

**Why:** Pre-trade stress testing. Before putting on a big trade, you want to know if it makes the worst-case scenario materially worse.

### 12. Scenario Descriptions and Context

Each scenario should show a tooltip or expandable panel with: what the scenario represents historically, what the vol and price shocks actually are, when it last ran, and who approved it.

**Why:** Institutional knowledge. Not everyone on the desk traded through 2008. The metadata is educational and gives context to the numbers.

---

## Recommended Layout

1. **Top: Control Bar** — "Run All Scenarios" button (primary), scenario multi-select for comparison, custom scenario builder (expandable panel or modal). Time horizon and confidence level selectors.

2. **Middle: Scenarios Comparison Table** — All scenarios in rows, sorted by worst P&L impact. Columns: Scenario, Status (approved/draft), Base VaR, Stressed VaR, VaR Multiplier, P&L Impact, Limit Breaches (count, color-coded). Each row expandable/clickable to show asset class breakdown.

3. **Bottom: Detail Panel** — When a scenario row is selected, show full breakdown: asset class impact bars plus position-level drill-down table. Tabs within this panel for "Asset Class View" and "Position View."

---

## Backend Gap Analysis

The backend is significantly more capable than what the UI exposes:

| Capability | Backend | UI |
|---|---|---|
| Custom vol/price shocks per asset class | Yes | No |
| Multiple calculation types (Parametric/Historical/MC) | Yes | No |
| Confidence level selection | Yes | No |
| Time horizon parameter | Yes | No |
| Scenario governance (CRUD + approval workflow) | Yes | No |
| Hypothetical scenario builder | Yes | No |
| Number of simulations (Monte Carlo) | Yes | No |

**Bottom line:** The single most impactful change is run-all with a comparison table and position drill-down. That turns this from a demo into something usable at 7am when reviewing the book.
