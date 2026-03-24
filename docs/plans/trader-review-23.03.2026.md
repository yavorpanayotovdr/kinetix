# Trader Review — 23 March 2026

Full system review by Marcus (senior trader persona, 25+ years Wall Street & London).
Assessed the entire Kinetix platform: UI, risk engine, backend services, data model.

---

## What Works Well

**Foundation is solid.** Genuine multi-service risk platform — three VaR methodologies (parametric, historical, Monte Carlo with antithetic variates), proper Greeks including cross-Greeks (vanna, volga, charm), FRTB capital charges, stress testing with historical scenarios, hash-chained audit trail.

**Instrument type hierarchy is clean.** Eleven types, sealed interface in Kotlin, typed Python position subtypes, proto definitions — data model flows cleanly from reference data through to risk calculation.

**UI has the right bones.** Nine tabs covering positions, trades, P&L, risk, EOD, scenarios, regulatory, alerts, system health. What-if analysis, run comparison, cross-book aggregation, dark mode — workflows, not just screens.

**Event-driven architecture with correlation IDs.** Tracing a risk result back through the calculation chain to the triggering trade — critical during a live crisis.

---

## Directions — Prioritised

### Direction 1: Real-Time Intraday P&L (HIGH PRIORITY)

P&L attribution is currently SOD-baseline-to-current. Fine for EOD reporting. A live desk needs **streaming intraday P&L** — updated every tick and every trade.

During the March 2020 sell-off, the desks that survived were the ones where the PM could see P&L moving in real-time and cut risk before the daily batch told them they were down $50M. WebSocket infrastructure already exists for prices — extend it to push P&L deltas.

**What this looks like:**
- P&L ticker on every screen (always visible, not buried in a tab)
- Intraday P&L chart with trade markers ("I hedged at 2pm and P&L stabilised")
- Decomposition into realised vs. unrealised, by asset class, updating live
- SOD, intraday high-water mark, and current — three numbers at a glance

---

### Direction 2: Hedge Recommendation Engine (HIGH PRIORITY)

The what-if panel lets users enter hypothetical trades and see risk impact. That's backwards. **The system should tell the user what to trade to reduce risk.**

When vega is $2M per vol point and vol just moved 3 points against you, manually entering 15 hypothetical option trades to find the right hedge is too slow. The system should say: "Buy 500 SPX 4000 puts to reduce portfolio vega by 60% — cost: $1.2M premium, residual vega: $800K."

**What this looks like:**
- "Suggest Hedge" button on the risk tab
- Target: minimise a specific Greek (delta, vega, gamma) or VaR
- Constrained optimisation: respect position limits, use liquid instruments only
- Show cost of hedge (premium, carry, bid-ask crossing cost)
- One-click export to what-if for validation before execution

---

### Direction 3: Liquidity Risk Layer (HIGH PRIORITY)

No liquidity risk exists. VaR assumes everything can be liquidated at mid-price in one day.

In 2008, banks reported "1-day 99% VaR: $50M" while sitting on illiquid structured credit they couldn't sell for three months. The VaR number was technically correct and completely useless.

**What this looks like:**
- Liquidity score per instrument (based on average daily volume, bid-ask spread, market depth)
- Liquidity-adjusted VaR (LVaR): scale holding period by position size / ADV
- Concentration risk: flag when >5% of ADV in any instrument
- Liquidation horizon analysis: "At normal market volumes, this book takes 3.2 days to unwind"
- Stressed liquidity: haircuts on liquidation values under crisis conditions

---

### Direction 4: Factor-Based Risk Decomposition (MEDIUM-HIGH)

Risk decomposition is by asset class and by position. A PM managing a $2B multi-strategy book needs **factor decomposition**.

"How much of my P&L is driven by the S&P, how much by rates, how much by credit spreads, how much by vol, and how much is idiosyncratic?" If 80% of risk is just long S&P beta, 200 positions aren't needed — one futures contract and a lot less complexity.

**What this looks like:**
- Risk factor model: equity beta, rates duration, credit spread DV01, FX delta, vol exposure
- Factor attribution: decompose VaR into systematic vs. idiosyncratic
- Factor P&L attribution: "Today's -$3M P&L was: -$2.1M S&P beta, -$0.5M rates, -$0.4M idiosyncratic"
- Correlation to key macro factors displayed on the risk dashboard
- Factor concentration warnings

---

### Direction 5: Order/Execution Management Integration (MEDIUM)

Trades are booked after the fact — executed externally, then recorded. For a risk system to be truly useful, it needs to be in the execution loop.

Not a full OMS build, but:
- **FIX protocol adapter** to receive fills from brokers/exchanges in real-time
- **Pre-trade risk gateway**: every order passes through limit checks before hitting the market
- **Position reconciliation**: compare internal positions against prime broker/custodian statements, flag breaks
- **Execution cost analysis**: track slippage (execution price vs. arrival price) to measure cost of hedging

Turns Kinetix from "a system I look at after trading" to "a system that's part of how I trade."

---

### Direction 6: Historical Scenario Replay & Custom Stress Builder (MEDIUM)

Four pre-built stress scenarios (GFC, COVID, taper tantrum, Euro crisis) are a start. Regulators and risk committees want more.

**What this looks like:**
- **Historical replay**: "Show me what my current portfolio would have lost during the week of March 16, 2020" — actual historical returns, not parameterised shocks
- **Reverse stress testing**: "What market move would cause a $100M loss?" — work backwards from loss to scenario
- **Custom multi-factor scenarios**: shock equity -15%, vol +50%, rates -100bps, credit spreads +200bps simultaneously with correlation overrides
- **Scenario library**: save, share, and version custom scenarios across the desk
- Stress engine already exists — this is about flexibility and power

---

### Direction 7: Counterparty & Credit Risk Dashboard (MEDIUM)

Counterparty exposure aggregation exists but is basic. On a real desk, counterparty risk is a daily conversation with credit risk officers.

**What this looks like:**
- **Potential Future Exposure (PFE)**: Monte Carlo simulation of future exposure paths per counterparty
- **Credit Valuation Adjustment (CVA)**: mark-to-market the counterparty credit risk
- **Wrong-way risk flags**: "Largest exposure to Bank X is in the same sector Bank X operates in"
- **Netting set visualisation**: gross vs. net exposure per ISDA agreement
- **Collateral tracking**: posted/received margin, margin call projections

---

### Direction 8: Multi-Desk Risk Aggregation & Reporting (MEDIUM-LOW)

Cross-book VaR exists. But a head of risk thinks in desks, strategies, and business lines.

**What this looks like:**
- Drill-down hierarchy: Firm → Division → Desk → Book → Position → Trade
- Each level: VaR, P&L, limit utilisation, top 5 contributors
- Marginal contribution: "If I shut down the EM credit desk, firm VaR drops by $X"
- Risk budgeting: allocate VaR limits top-down, track utilisation bottom-up
- Daily risk report PDF: auto-generated for the CRO, no manual work

---

### Direction 9: Market Regime Detection (LOWER PRIORITY, HIGH VALUE)

The ML module (LSTM vol predictor, anomaly detector) is disconnected from the risk workflow.

**What this looks like:**
- **Regime classifier**: normal, elevated vol, crisis — based on realised vol, VIX, credit spreads, correlation structure
- **Adaptive risk parameters**: in crisis regime, automatically switch to stressed correlations, wider confidence intervals, shorter holding periods
- **Early warning system**: "Correlation structure has shifted to resemble pre-GFC patterns" — surface as alert before VaR catches up
- **Dynamic VaR model selection**: historical VaR in normal regimes, Monte Carlo in stressed regimes

---

### Direction 10: Algo Trading & Systematic Strategy Support (FUTURE)

For systematic/quant desk support:
- **Backtesting framework for strategies** (not just VaR models)
- **Signal generation and alpha capture** tracking
- **Transaction cost modelling** (market impact, commission, financing)
- **Portfolio rebalancing engine** with optimiser constraints

Big build. Park for now, keep architecture extensible.

---

## The Bottom Line

Phase 1 is complete — position management, risk calculation, regulatory reporting, audit. Strong foundation.

**Phase 2 is about making it a decision-support tool**, not just a reporting tool. Priority order:

1. **Real-time intraday P&L** — table stakes for any live desk
2. **Hedge recommendations** — turn "here's your risk" into "here's what to do about it"
3. **Liquidity risk** — the missing dimension that makes every other number suspect
4. **Factor decomposition** — the lens PMs actually think in

Those four transform this from "good risk reporting system" to "system I can't trade without."
