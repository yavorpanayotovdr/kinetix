# AI Calculation Mode

## Motivation

Kinetix supports a traditional mathematical approach to risk calculation (parametric VaR, Monte Carlo, Black-Scholes, FRTB). A second **AI-driven calculation mode** would run alongside the analytical mode, producing the same output schemas so results can be compared side-by-side.

AI does not replace analytical models -- it **augments** them. The traditional approach has regulatory backing and interpretability. AI excels where the analytical model has known weaknesses (normal distribution assumptions, static parameters) or where computational shortcuts are valuable.

---

## Tier 1: High Value, Directly Comparable

### 1. Neural Network VaR Surrogate

Train a network to approximate Monte Carlo VaR output.

- **Input:** portfolio composition + market state features
- **Output:** VaR estimate
- **Why:** MC with 10K paths is slow; a trained network gives near-equivalent accuracy in milliseconds. Direct side-by-side comparison with existing MC/parametric VaR. Both can be shown on the UI to track how closely the AI tracks the math.
- **Research area:** Neural network acceleration of MC is used in production at some banks.

### 2. Generative Scenario Models (VAE/GAN)

Train a generative model on historical market data to produce realistic correlated market moves, instead of hand-crafted stress scenarios.

- **Input:** historical market data (price series, correlations)
- **Output:** synthetic but realistic market scenarios
- **Why:** The AI learns the joint distribution of market moves that the parametric model assumes is normal (and it isn't). Generated scenarios feed into the existing stress testing engine.

### 3. Neural Volatility Surface

Replace bilinear interpolation on `VolSurface` with a neural network that learns the arbitrage-free implied vol surface.

- **Input:** (strike, maturity, market features)
- **Output:** implied volatility
- **Why:** The vol surface is already a key input to Black-Scholes pricing -- swapping the interpolation layer with a learned model is a clean, contained change. Well-studied problem with good literature.

---

## Tier 2: High Learning Value, Extends the Platform

### 4. LLM-Powered Risk Commentary

Use the Claude API to generate natural language explanations of risk changes.

- **Input:** VaR breakdown, Greeks, position changes
- **Output:** Plain-English summary, e.g. *"Portfolio VaR increased 12% driven by rising equity concentration and elevated cross-asset correlations following the rate announcement."*
- **Why:** Uses the Claude API directly, adds genuine user value. Traders want plain-English risk summaries alongside the numbers.

### 5. Regime Detection / Market State Classification

Train a model to classify the current market regime (low-vol, trending, crisis, mean-reverting) from recent price history.

- **Input:** recent price series, volatility, correlation changes
- **Output:** regime label + confidence
- **Use:** dynamically select VaR parameters (confidence level, decay factor, correlation model) based on detected regime.
- **Why:** Addresses a real weakness of static models -- they use the same parameters in calm and crisis markets.

### 6. Deep Hedging / Learned Greeks

Train a network to predict Greeks directly from portfolio state, bypassing finite-difference bumping that requires multiple repricings.

- **Input:** portfolio positions + market state
- **Output:** delta, gamma, vega per position
- **Why:** Active research area at quant desks. Eliminates the cost of repeated repricing for sensitivity calculation.

---

## Dual-Mode Architecture

Introduce a **calculation mode** enum (`ANALYTICAL` | `AI`) at the request level, flowing through the existing gRPC interface. Both modes produce the same output schema (`VaRResponse`, `ValuationResponse`), so the UI can show them side-by-side or let the user toggle.

```
Request (mode=AI)
    |
Same gRPC service -> same converters
    |
Dispatch to AI calculation pipeline instead of analytical
    |
Same output protobuf -> same UI rendering
```

This gives a **model comparison dashboard** for free -- show analytical vs. AI VaR on the same chart, track divergence, and use backtesting to evaluate which performs better.

---

## Recommended Starting Point

**Neural VaR Surrogate (#1) + LLM Risk Commentary (#4)**

These are complementary and produce the most visible results:
- The surrogate model is pure ML/quant work (PyTorch, training pipeline, evaluation)
- The commentary is LLM/API work (Claude API integration, prompt engineering)
- Together they give a compelling "AI mode" that is both quantitatively rigorous and user-facing

---

## Existing Foundation

The risk-engine already has an `ml/` module with:
- `vol_predictor.py` -- LSTM volatility prediction
- `anomaly_detector.py` -- Isolation Forest anomaly detection
- `credit_model.py` -- Credit risk classification
- `data_generator.py` -- Synthetic training data generation
- `model_store.py` -- Torch model loading/caching
- `ml_server.py` -- MLPredictionService gRPC entrypoint

The new AI calculation mode would extend this foundation.
