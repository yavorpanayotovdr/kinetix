# Risk Engine — VaR, Expected Shortfall, gRPC Server

Python-based quantitative risk calculation engine for the Kinetix platform.

## Modules

| Module | Purpose |
|--------|---------|
| `models.py` | Domain types: `AssetClass`, `ConfidenceLevel`, `PositionRisk`, `VaRResult`, etc. |
| `volatility.py` | Asset class volatility assumptions (20% equity, 6% FI, etc.) + 5x5 correlation matrix |
| `expected_shortfall.py` | Empirical CVaR — mean of losses beyond VaR threshold |
| `var_parametric.py` | Closed-form variance-covariance VaR with analytical ES and Euler component allocation |
| `var_historical.py` | Synthetic returns via Cholesky decomposition, empirical percentile |
| `var_monte_carlo.py` | Correlated return simulation, deterministic with seed, convergence-verified |
| `portfolio_risk.py` | Orchestrator: groups positions by asset class, assigns vols, dispatches to VaR engine |
| `converters.py` | Proto <-> domain mapping |
| `server.py` | `RiskCalculationServicer` with unary `CalculateVaR` + bidirectional streaming `CalculateVaRStream` |

## VaR calculation methods

### Parametric (variance-covariance)
Closed-form: `VaR = z * sqrt(w' * Cov * w) * sqrt(T)` where `Cov` is built from daily volatilities and the correlation matrix. ES uses the analytical normal formula: `ES = sigma * phi(z) / (1 - alpha)`. Component VaR via Euler allocation sums exactly to total.

### Historical
Generates `num_scenarios` (default 250) synthetic daily returns from a multivariate normal distribution using Cholesky decomposition of the correlation matrix. VaR is the empirical percentile of simulated portfolio losses. Converges to parametric VaR with enough scenarios.

### Monte Carlo
Same simulation approach as historical but with configurable `num_simulations` (default 10,000). Accepts a `seed` parameter for reproducibility. Converges to parametric VaR for normally distributed returns.

### Expected Shortfall (CVaR)
Mean of losses exceeding the VaR threshold. Parametric VaR computes ES analytically; historical and Monte Carlo use the empirical tail mean.

## Asset class assumptions

| Asset Class | Annualized Volatility |
|-------------|----------------------|
| EQUITY | 20% |
| FIXED_INCOME | 6% |
| FX | 10% |
| COMMODITY | 25% |
| DERIVATIVE | 30% |

Cross-asset correlations are defined in a 5x5 positive-definite matrix (e.g., equity-fixed income: -0.20, equity-derivative: 0.70).

## gRPC contract

Implements `RiskCalculationService` from `proto/src/main/proto/kinetix/risk/risk_calculation.proto`:
- `CalculateVaR(VaRRequest) -> VaRResponse` — unary RPC
- `CalculateVaRStream(stream VaRRequest) -> stream VaRResponse` — bidirectional streaming

Proto stubs are generated via `scripts/generate_proto.sh` into `src/kinetix_risk/proto/`.

## Running

```bash
# Install dependencies
uv sync

# Run tests (57 tests)
uv run pytest -v

# Generate proto stubs (requires grpcio-tools)
./scripts/generate_proto.sh

# Start gRPC server on port 50051
uv run python -m kinetix_risk.server
```
