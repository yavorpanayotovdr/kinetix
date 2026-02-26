# Scenarios Tab — Stress Testing & What-If Analysis

The Scenarios tab lets traders and risk managers replay historical crises against their current portfolio and run custom hypothetical shocks to understand tail-risk exposure.

---

## What it displays

- **Scenario Selector** — dropdown of predefined historical scenarios plus support for custom hypothetical shocks
- **Run Stress Test** button (red, lightning icon)
- **Results Summary** — four key metrics:
  - Scenario name
  - Base VaR (normal conditions)
  - Stressed VaR (under the scenario)
  - P&L Impact (total loss)
- **Asset Class Impact Chart** — bar chart comparing base vs. stressed exposure per asset class, with P&L impact, color-coded:
  - EQUITY (blue), FIXED_INCOME (green), COMMODITY (amber), FX (purple), DERIVATIVE (red)

---

## Predefined historical scenarios

| Scenario | Description | Equity Shock | Commodity Shock | FX Shock | FI Shock | Vol Multiplier (Equity) |
|----------|-------------|-------------|----------------|----------|----------|------------------------|
| **GFC 2008** | Global Financial Crisis — severe equity/commodity selloff | -40% | -30% | -15% | -5% | 3.0x |
| **COVID 2020** | Pandemic — rapid equity decline, commodity crash | -35% | -25% | -10% | -3% | 2.5x |
| **Taper Tantrum 2013** | Fixed income selloff, moderate equity decline | -5% | -3% | -5% | -10% | 1.5x |
| **Euro Crisis 2011** | European sovereign debt crisis | -20% | -10% | -15% | -8% | 2.0x |

Each scenario includes:
- **Price shocks** per asset class (how much prices drop)
- **Volatility shocks** per asset class (how much vol spikes)
- **Correlation overrides** (optional) — crisis correlations reflecting assets moving together

---

## How stress tests are calculated

1. Compute **base VaR** with current volatilities and correlations
2. Apply **price shocks** — multiply each position's market value by the shock factor (e.g. 0.60 = 40% loss)
3. Build **stressed volatility matrix** — multiply default vols by scenario vol multipliers
4. Build **stressed correlation matrix** — use crisis-specific overrides if defined
5. Compute **stressed VaR** with the shocked inputs
6. Calculate **per-asset-class impact** — stressed exposure minus base exposure = P&L
7. Return results with base VaR, stressed VaR, total P&L impact, and per-class breakdown

---

## Custom hypothetical scenarios

Beyond predefined scenarios, the API accepts custom shocks:
- Arbitrary price shock per asset class (e.g. "what if equities drop 50% but commodities rally 10%?")
- Arbitrary volatility multiplier per asset class
- Custom description

This enables desk-specific what-if analysis without waiting for a predefined template.

---

## Why a trader / investment bank needs this

1. **Regulatory requirement** — Regulators require banks to demonstrate they understand how historical crises would impact their current book.
2. **Tail risk quantification** — VaR tells you about "normal bad days"; stress testing tells you about catastrophic days that fall outside VaR confidence intervals.
3. **Hedging validation** — A portfolio hedged against normal volatility may still blow up in a crisis if correlations spike to 1. Stress tests with correlation overrides reveal this.
4. **Scenario planning** — The desk can model custom shocks ("what if oil drops 40% overnight?") to prepare contingency plans.
5. **Asset class comparison** — The impact chart immediately shows which asset classes are most vulnerable in each crisis, guiding allocation decisions.
6. **Risk committee reporting** — Stress test results feed directly into risk committee presentations and board-level risk reports.

---

## Architecture

```
UI (StressTestPanel)
  → Risk Orchestrator (Ktor, HTTP REST)
    → Risk Engine (Python, gRPC StressTestService)
      ├── Scenario lookup or custom build
      ├── Base VaR calculation
      ├── Apply price & vol shocks
      ├── Stressed VaR calculation
      └── Per-asset-class impact breakdown
    → Response back through gRPC → HTTP → UI
```

---

## Key files

| Component | Location |
|-----------|----------|
| UI Component | `ui/src/components/StressTestPanel.tsx` |
| Stress Test Hook | `ui/src/hooks/useStressTest.ts` |
| Greeks Hook | `ui/src/hooks/useGreeks.ts` |
| API Client | `ui/src/api/stress.ts` |
| Risk Orchestrator Routes | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskRoutes.kt` |
| Stress Test Request DTO | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/StressTestRequestBody.kt` |
| Stress Test Response DTO | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/dtos/StressTestResponse.kt` |
| Scenario Definitions | `risk-engine/src/kinetix_risk/stress/scenarios.py` |
| Stress Engine | `risk-engine/src/kinetix_risk/stress/engine.py` |
| Scenario Builder | `risk-engine/src/kinetix_risk/stress/builder.py` |
| gRPC Stress Server | `risk-engine/src/kinetix_risk/stress_server.py` |
| Proto Definitions | `proto/src/main/proto/kinetix/risk/stress_testing.proto` |

---

## API Endpoints

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/v1/risk/stress/scenarios` | GET | List available stress scenarios |
| `/api/v1/risk/stress/{portfolioId}` | POST | Run stress test for a portfolio |
| `/api/v1/risk/greeks/{portfolioId}` | POST | Calculate Greeks (sensitivity analysis) |

---

## Request / Response

### Stress test request
```json
{
  "scenarioName": "GFC_2008",
  "calculationType": "PARAMETRIC",
  "confidenceLevel": "CL_99",
  "timeHorizonDays": "1"
}
```

Custom scenario:
```json
{
  "scenarioName": "CUSTOM_OIL_SHOCK",
  "description": "Oil price collapse scenario",
  "priceShocks": { "COMMODITY": -0.40, "EQUITY": -0.10 },
  "volShocks": { "COMMODITY": 2.5, "EQUITY": 1.5 }
}
```

### Stress test response
```json
{
  "scenarioName": "GFC_2008",
  "baseVar": "100000.00",
  "stressedVar": "300000.00",
  "pnlImpact": "-550000.00",
  "assetClassImpacts": [
    {
      "assetClass": "EQUITY",
      "baseExposure": "1000000.00",
      "stressedExposure": "600000.00",
      "pnlImpact": "-400000.00"
    }
  ],
  "calculatedAt": "2026-02-26T14:00:00Z"
}
```
