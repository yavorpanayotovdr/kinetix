# Regulatory Tab — FRTB Capital Reporting

The Regulatory tab implements **FRTB (Fundamental Review of the Trading Book)**, the Basel III standard that dictates how much capital an investment bank must hold against its trading book risk.

---

## What it displays

A dashboard with three sections:

1. **Capital Charge Summary** — four headline numbers:
   - **Total Capital** (the bottom line the regulator cares about)
   - **SbM** (Sensitivities-based Method)
   - **DRC** (Default Risk Charge)
   - **RRAO** (Residual Risk Add-On)

2. **Proportions Bar** — a visual breakdown showing what percentage of the total comes from each component.

3. **SbM Breakdown Table** — per-risk-class detail (Delta, Vega, Curvature charges) across seven risk classes: GIRR, CSR (securitised and non-securitised), Equity, Commodity, and FX.

It also provides **Calculate FRTB**, **Download CSV**, and **Download XBRL** actions.

---

## The three pillars of the FRTB calculation

| Component | What it captures | How it's calculated |
|-----------|-----------------|---------------------|
| **SbM** | Market risk sensitivities | Applies regulatory risk weights (e.g. Equity delta 20%, FX delta 10%) to position exposures, then aggregates using intra-bucket correlations and three inter-bucket correlation scenarios (low/medium/high), taking the most conservative result |
| **DRC** | Credit default risk | Computes Jump-to-Default per position (`market_value x default_prob x LGD`), sums long/short, gives a 50% hedge benefit for natural offsets, yielding Net DRC |
| **RRAO** | Residual risks not captured above | 1% of exotic derivative notional + 0.1% of all other notional — a simple "catch-all" add-on |

**Total Capital Charge = SbM + DRC + RRAO**

---

## Why a trader / investment bank needs this

1. **Regulatory compliance** — Banks *must* report FRTB capital to regulators. The XBRL export produces the standard format regulators expect for submission.

2. **Capital efficiency** — Capital held against trading risk is capital you can't deploy. By seeing which risk classes drive the largest charges (e.g. Equity delta at 20% weight vs GIRR at 1.5%), the desk can identify where to optimise positions or add hedges to reduce the charge.

3. **Hedging insight** — The DRC hedge benefit quantifies how much natural offset exists between long and short credit positions. A trader can see whether adding a hedge actually reduces their capital requirement.

4. **Risk concentration visibility** — The per-risk-class breakdown reveals where risk is concentrated. If 70% of your SbM charge comes from Equity, that's a signal to the risk committee.

5. **Historical tracking** — The regulatory service persists every calculation, so risk managers can track how capital requirements evolve over time and detect trends before they become problems.

6. **Audit trail** — CSV and XBRL exports create a paper trail for internal audit and regulatory examination.

---

## Architecture

```
UI (RegulatoryDashboard)
  -> Risk Orchestrator (Ktor, HTTP routes)
    -> Risk Engine (Python, gRPC) — runs the actual FRTB math
    -> Regulatory Service (Ktor) — persists results & history
```

The heavy calculation lives in the Python risk engine (`risk-engine/src/kinetix_risk/frtb/`), with separate modules for SbM, DRC, RRAO, risk weights, and report generation.

### Key files

| Component | Location |
|-----------|----------|
| UI Component | `ui/src/components/RegulatoryDashboard.tsx` |
| UI Hook | `ui/src/hooks/useRegulatory.ts` |
| UI API | `ui/src/api/regulatory.ts` |
| Risk Orchestrator Routes | `risk-orchestrator/src/main/kotlin/com/kinetix/risk/routes/RiskRoutes.kt` |
| Regulatory Service Routes | `regulatory-service/src/main/kotlin/com/kinetix/regulatory/routes/RegulatoryRoutes.kt` |
| FRTB Calculator | `risk-engine/src/kinetix_risk/frtb/calculator.py` |
| SbM Calculation | `risk-engine/src/kinetix_risk/frtb/sbm.py` |
| DRC Calculation | `risk-engine/src/kinetix_risk/frtb/drc.py` |
| RRAO Calculation | `risk-engine/src/kinetix_risk/frtb/rrao.py` |
| Report Generation | `risk-engine/src/kinetix_risk/frtb/report.py` |
| Risk Weights | `risk-engine/src/kinetix_risk/frtb/risk_weights.py` |
| Proto Definitions | `proto/src/main/proto/kinetix/risk/regulatory_reporting.proto` |

---

## API Endpoints

### Risk Orchestrator

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/v1/regulatory/frtb/{portfolioId}` | POST | Calculates FRTB using gRPC call to risk-engine |
| `/api/v1/regulatory/report/{portfolioId}` | POST | Generates CSV/XBRL reports |

### Regulatory Service

| Route | Method | Purpose |
|-------|--------|---------|
| `/api/v1/regulatory/frtb/{portfolioId}/calculate` | POST | Calls risk-orchestrator, stores result in database |
| `/api/v1/regulatory/frtb/{portfolioId}/history` | GET | Retrieves paginated calculation history |
| `/api/v1/regulatory/frtb/{portfolioId}/latest` | GET | Gets most recent calculation |

---

## Regulatory Risk Weights

| Risk Class | Delta Weight | Vega Weight |
|------------|-------------|-------------|
| GIRR | 1.5% | 1.0% |
| CSR Non-Sec | 3.0% | 2.0% |
| CSR Sec CTP | 4.0% | 3.0% |
| CSR Sec Non-CTP | 6.0% | 4.0% |
| Equity | 20.0% | 15.0% |
| Commodity | 15.0% | 10.0% |
| FX | 10.0% | 8.0% |

## DRC Default Probabilities

| Rating | Default Probability |
|--------|-------------------|
| Investment Grade | 0.3% |
| Speculative | 3.0% |
| Unrated (default) | 1.5% |

Loss Given Default (LGD): 60%
