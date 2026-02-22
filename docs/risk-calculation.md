# Risk Calculation Architecture

## Architecture Overview

Kinetix is a polyglot microservices platform with the risk calculation spread across several services:

```
Position Service (Kotlin) -> Risk Orchestrator (Kotlin) -> Risk Engine (Python) -> Gateway (Kotlin) -> UI (React)
```

## VaR Calculation — Three Methods

The Python **Risk Engine** (`risk-engine/src/kinetix_risk/`) supports three VaR calculation approaches:

### 1. Parametric (Variance-Covariance)

- Closed-form: `VaR = z * sqrt(w' * Cov * w) * sqrt(T)`
- Fastest — used for the scheduled background calculation (every 60s)
- Analytical Expected Shortfall: `ES = sigma * phi(z) / (1 - alpha)`
- Component VaR via Euler allocation (decomposes risk by asset class)

### 2. Historical / Scenario

- Generates 250 synthetic daily returns using Cholesky decomposition of the correlation matrix
- VaR = empirical percentile of simulated portfolio losses

### 3. Monte Carlo

- Same Cholesky approach but with 10,000 simulations (configurable)
- Better tail estimation, handles non-linear payoffs

## Key Inputs

| Input | Source |
|-------|--------|
| **Positions** | Position Service (PostgreSQL) — aggregated by asset class |
| **Volatilities** | Hardcoded defaults (Equity 20%, Fixed Income 6%, FX 10%, Commodity 25%, Derivative 30%) with optional LSTM model overrides |
| **Correlations** | 5x5 positive-definite matrix (e.g., Equity-Fixed Income = -0.20 for diversification) |
| **Market Prices** | Market Data Service -> Redis cache (simulated feed with +/-2% daily moves) |

## Data Flow

1. **Risk Orchestrator** fetches positions from Position Service
2. Positions are sent via gRPC to the Python Risk Engine
3. Risk Engine aggregates positions by asset class, applies volatilities + correlations
4. Dispatches to the selected VaR calculator (parametric/historical/MC)
5. Returns VaR, Expected Shortfall, and component breakdown
6. Results are cached in-memory by the orchestrator (`LatestVaRCache`)
7. Gateway exposes REST endpoints; UI polls every 30 seconds

## Beyond VaR

The risk engine also provides:

- **Stress Testing** — historical scenarios (GFC 2008, COVID 2020, Taper Tantrum 2013, Euro Crisis 2011) that shock vols, correlations, and prices
- **Greeks** — delta, gamma, vega, theta, rho via finite-difference bumping against parametric VaR
- **FRTB Regulatory Capital** — Basel 3.1 standardised approach (SBM + DRC + RRAO)
- **ML Models** — LSTM volatility forecasting, Isolation Forest anomaly detection, credit default probability

## Scheduling

The `ScheduledVaRCalculator` runs parametric VaR (95% CL, 1-day horizon) every 60 seconds for all portfolios, keeping the cache fresh. On-demand calculations (any method) can be triggered via POST from the UI.

## Mathematical Foundations

### Parametric VaR

```
Daily_Vol = Annual_Vol / sqrt(252)
Dollar_Vols = Daily_Vols * Market_Values
Cov_Matrix = outer(Dollar_Vols, Dollar_Vols) . Correlation
Portfolio_Variance = 1^T * Cov * 1
Portfolio_StdDev = sqrt(Portfolio_Variance)
VaR = norm.ppf(alpha) * StdDev * sqrt(T)
ES = StdDev * norm.pdf(z) / (1 - alpha) * sqrt(T)
```

### Cholesky Decomposition (Historical / Monte Carlo)

```
Chol = Cholesky(Correlation)
Z ~ N(0,1) [random normal, shape: (num_scenarios, n_assets)]
Correlated_Returns = Z @ Chol^T . Daily_Vols
Portfolio_Loss = -Correlated_Returns @ Market_Values
VaR = percentile(Portfolio_Loss, alpha * 100)
ES = mean(Portfolio_Loss[Portfolio_Loss >= VaR])
```

### Greeks Finite Difference

```
Delta = (VaR(S+1%) - VaR(S)) / 0.01
Gamma = (VaR(S+1%) - 2*VaR(S) + VaR(S-1%)) / 0.01^2
Vega  = (VaR(sigma+1pp) - VaR(sigma)) / 0.01
Theta = VaR(T-1) - VaR(T)
Rho   = (VaR(sigma_rates+1bp) - VaR(sigma)) / 0.0001
```

## Asset Class Volatility Assumptions

| Asset Class | Annual Volatility |
|-------------|-------------------|
| EQUITY | 20% |
| FIXED_INCOME | 6% |
| FX | 10% |
| COMMODITY | 25% |
| DERIVATIVE | 30% |

## Correlation Matrix

- Equity-Derivative: 0.70 (high positive)
- Equity-Fixed Income: -0.20 (diversification benefit)
- Equity-Commodity: 0.40
- Equity-FX: 0.30
- Commodity-Derivative: 0.35
- Fixed Income-FX: -0.10
