# Generic Finance Glossary

General financial terms, acronyms, and concepts referenced throughout the Kinetix platform.

---

## Risk Metrics

| Term | Definition |
|------|-----------|
| **VaR (Value-at-Risk)** | Maximum expected loss at a given confidence level over a specified time horizon. Common levels: 95%, 99%, 99.7%. |
| **Expected Shortfall (ES)** | Average loss beyond the VaR threshold. Also called Conditional VaR (CVaR). Captures tail risk that VaR misses. |
| **Historical VaR** | VaR computed by replaying actual historical returns against current positions. |
| **Parametric VaR** | VaR assuming returns follow a normal distribution. Uses mean and covariance matrix. |
| **Monte Carlo VaR** | VaR estimated by simulating thousands of random price paths and measuring the loss distribution. |
| **Marginal VaR** | Sensitivity of portfolio VaR to a small change in a position's weight (dVaR/dw). |
| **Incremental VaR** | Change in portfolio VaR when a position or book is added or removed. |
| **LVAR (Liquidity-adjusted VaR)** | VaR adjusted for the cost and time required to liquidate positions. |
| **Confidence Level** | Probability threshold for VaR — e.g. 99% means "we expect losses to exceed this level only 1% of the time." |

## Greeks (Option Sensitivities)

| Term | Definition |
|------|-----------|
| **Delta** | Rate of change of option price with respect to the underlying price. Measures directional exposure. |
| **Gamma** | Rate of change of delta with respect to the underlying price. Measures convexity. |
| **Vega** | Sensitivity of option price to changes in implied volatility. |
| **Theta** | Rate of option value decay over time, all else equal. |
| **Rho** | Sensitivity of option price to changes in interest rates. |
| **Vanna** | Cross-sensitivity: rate of change of delta with respect to volatility (d2V/dS*dsigma). |
| **Volga** | Sensitivity of vega to changes in volatility. Also called vega convexity or vomma. |
| **Charm** | Rate of change of delta over time (dDelta/dt). Also called delta decay. |

## Pricing Models

| Term | Definition |
|------|-----------|
| **Black-Scholes** | Analytical closed-form model for pricing European options. Assumes lognormal price distribution, constant volatility, and no dividends. |
| **Black-Scholes-Merton** | Extension of Black-Scholes incorporating continuous dividend yields. |
| **Present Value (PV)** | Current worth of future cash flows, discounted at an appropriate rate. |
| **Mark-to-Market (MtM)** | Valuing positions at current market prices rather than historical cost. |
| **Implied Volatility (IV)** | Volatility backed out from observed option prices using a pricing model. |
| **Historical Volatility** | Standard deviation of historical asset returns over a lookback window. |

## Volatility & Correlation

| Term | Definition |
|------|-----------|
| **EWMA (Exponentially Weighted Moving Average)** | Volatility estimator that gives more weight to recent observations. Decay factor lambda typically 0.94. |
| **Volatility Surface** | 3D structure of implied volatility varying by strike price and time to expiry. |
| **Correlation Matrix** | Square matrix of pairwise asset correlations used in portfolio risk aggregation. |
| **Ledoit-Wolf Shrinkage** | Statistical technique that regularises a sample covariance matrix toward a structured target, reducing estimation error. |
| **Cholesky Decomposition** | Factorisation of a positive-definite matrix into a lower-triangular matrix. Used to generate correlated random variables in Monte Carlo simulation. |
| **Antithetic Variates** | Variance reduction technique that pairs each random path with its mirror image, improving Monte Carlo convergence. |

## Market Data

| Term | Definition |
|------|-----------|
| **Spot Price** | Current market price for immediate delivery. |
| **Yield Curve** | Term structure of interest rates across maturities. Basis for discounting and forward rate derivation. |
| **Tenor** | Standard time period on a curve (e.g. 1M, 3M, 6M, 1Y, 2Y, 5Y, 10Y, 30Y). |
| **Risk-Free Rate** | Theoretical return on a zero-risk investment. Typically derived from government bond yields or overnight rates (e.g. SOFR). |
| **Forward Curve** | Expected future prices or rates for a given instrument, derived from the spot curve. |
| **Credit Spread** | Yield premium above the risk-free rate, compensating for credit risk. |
| **Basis Points (bps)** | 1/100th of a percentage point. 100 bps = 1%. Standard unit for spreads and rate changes. |
| **CDS Spread (Credit Default Swap)** | Annual premium paid to buy protection against a credit default. Indicator of counterparty credit risk. |
| **Dividend Yield** | Annual dividends as a percentage of price. Affects option pricing via the cost-of-carry model. |
| **Day-Count Convention** | Rules for counting days between dates for interest accrual (e.g. Actual/360, 30/360, Actual/Actual). |

## Trading

| Term | Definition |
|------|-----------|
| **Trade** | A transaction to buy or sell a financial instrument. |
| **Position** | The net holding in an instrument, resulting from one or more trades. |
| **Book** | A logical grouping of positions, typically aligned to a trading desk or strategy. |
| **Portfolio** | An aggregation of one or more books. |
| **Notional** | The nominal or face amount used to calculate payments on a derivative (e.g. swap notional). |
| **Counterparty** | The other party in a trade or contract. |
| **Buy Side** | Asset managers, hedge funds, pension funds — entities that buy and hold securities. |
| **Sell Side** | Broker-dealers, investment banks — entities that facilitate trades and provide liquidity. |
| **Trade Blotter** | A log of all trades executed, typically showing time, instrument, quantity, price, and status. |
| **Unrealised P&L** | Gain or loss on open positions based on current market prices vs. entry prices. |
| **Realised P&L** | Gain or loss locked in when a position is closed or reduced. |

## Instrument Types

| Term | Definition |
|------|-----------|
| **Cash Equity** | Ordinary shares / stock in a company. |
| **Government Bond** | Debt security issued by a sovereign government. Considered low credit risk. |
| **Corporate Bond** | Debt security issued by a corporation. Higher credit risk than government bonds. |
| **FX Spot** | Foreign exchange transaction for immediate delivery (T+2). |
| **FX Forward** | Agreement to exchange currencies at a future date at a pre-agreed rate. |
| **Equity Option** | Right (not obligation) to buy (call) or sell (put) shares at a specified strike price by an expiry date. |
| **Equity Future** | Standardised contract to buy/sell an equity index or single stock at a future date. |
| **Commodity Future** | Standardised contract to buy/sell a commodity at a future date. |
| **Commodity Option** | Option on a commodity or commodity future. |
| **FX Option** | Option to exchange currencies at a specified rate. |
| **Interest Rate Swap** | Agreement to exchange fixed-rate and floating-rate interest payments on a notional principal. |
| **Strike Price** | The price at which an option holder can buy (call) or sell (put) the underlying. |
| **Expiry Date** | The date on which an option or derivative contract expires. |
| **European Option** | Option exercisable only at expiry. |
| **American Option** | Option exercisable at any time up to and including expiry. |
| **Face Value** | The principal amount of a bond, repaid at maturity. |
| **Coupon Rate** | The fixed interest rate paid periodically on a bond's face value. |
| **Seniority** | Priority ranking in a default (Senior, Subordinated). Affects recovery rates. |

## Asset Classes

| Term | Definition |
|------|-----------|
| **Equity** | Ownership stakes in companies (stocks, indices). |
| **Fixed Income** | Debt instruments paying periodic interest (bonds, notes). |
| **FX (Foreign Exchange)** | Currency pairs and their derivatives. |
| **Commodity** | Physical goods (oil, gold, agricultural products) and their derivatives. |
| **Derivative** | Contract whose value derives from an underlying asset (options, futures, swaps). |

## Counterparty & Credit Risk

| Term | Definition |
|------|-----------|
| **PFE (Potential Future Exposure)** | Maximum expected credit exposure at a future date at a given confidence level. |
| **EPE (Expected Positive Exposure)** | Time-weighted average of expected positive exposures to a counterparty. |
| **CVA (Credit Valuation Adjustment)** | Adjustment to fair value accounting for the possibility that a counterparty defaults. |
| **LGD (Loss Given Default)** | Percentage of exposure lost if a counterparty defaults. Typically 40-60%. |
| **PD (Probability of Default)** | Likelihood that a counterparty defaults within a given time period. |
| **EAD (Exposure at Default)** | Total exposure to a counterparty at the time of default. |
| **Netting Agreement** | Legal contract (e.g. ISDA Master Agreement) allowing offsetting of mutual obligations in a default. |
| **Netting Set** | Group of trades covered by a single netting agreement. |
| **Close-Out Netting** | Right to terminate and net all transactions with a defaulting counterparty. |
| **Wrong-Way Risk** | Risk that exposure to a counterparty increases as that counterparty's credit quality deteriorates. |

## Regulatory & Capital

| Term | Definition |
|------|-----------|
| **FRTB (Fundamental Review of the Trading Book)** | Basel III framework redesigning market risk capital requirements for banks. |
| **GIRR (General Interest Rate Risk)** | FRTB risk class for interest rate exposures. |
| **CSR (Credit Spread Risk)** | FRTB risk class for credit spread exposures. |
| **DRC (Default Risk Charge)** | FRTB capital charge for the risk of obligor default. Uses jump-to-default methodology. |
| **JTD (Jump-to-Default)** | Loss incurred if an obligor defaults immediately. |
| **RRAO (Residual Risk Add-On)** | FRTB additional charge for risks not captured by other components. |
| **SBM (Standardised Boundary Method)** | FRTB simplified approach for calculating sensitivities-based capital charges. |
| **Basel III** | International banking regulation framework covering capital adequacy, stress testing, and liquidity. |
| **SPAN (Standard Portfolio Analysis of Risk)** | Margin methodology used by exchanges to calculate initial margin for futures and options. |
| **SIMM (Standard Initial Margin Model)** | ISDA methodology for calculating initial margin on uncleared OTC derivatives. |
| **Four-Eyes Principle** | Governance control requiring two independent individuals (preparer and approver) to complete a sensitive action. |
| **Backtesting** | Comparing predicted risk metrics (e.g. VaR) against actual outcomes to validate model accuracy. |
| **Kupiec POF (Proportion of Failures)** | Statistical test checking whether VaR violation frequency matches the expected rate. |
| **Christoffersen Test** | Statistical test checking both frequency and clustering of VaR violations. |

## Stress Testing

| Term | Definition |
|------|-----------|
| **Stress Test** | Evaluation of portfolio impact under hypothetical adverse market conditions. |
| **Scenario** | A defined set of market shocks applied simultaneously (e.g. equity -20%, rates +200bps). |
| **Reverse Stress Test** | Finding the market conditions that would cause a specified level of loss. |
| **Shock** | A hypothetical instantaneous change in a market variable. |

## P&L Attribution

| Term | Definition |
|------|-----------|
| **P&L Attribution** | Decomposition of total P&L into components explained by each Greek (delta P&L, gamma P&L, vega P&L, theta P&L, rho P&L) plus an unexplained residual. |
| **High Water Mark** | Peak historical portfolio value, used as a reference for drawdown measurement. |
| **Factor Model** | Decomposition of returns into systematic risk factors (e.g. market beta, rates duration, credit spread, FX delta, vol exposure). |
| **R-squared** | Proportion of return variance explained by a factor model. Higher = better fit. |

## Key Rate Risk

| Term | Definition |
|------|-----------|
| **Key Rate Duration (KRD)** | Sensitivity of a bond or portfolio to a 1bp move at a specific yield curve tenor. |
| **DV01 (Dollar Value of 01)** | Dollar change in value for a 1 basis point parallel shift in rates. |

## Liquidity Risk

| Term | Definition |
|------|-----------|
| **Liquidation Horizon** | Estimated time to exit a position without excessive market impact. |
| **Liquidity Tier** | Classification of asset liquidity (e.g. highly liquid, moderately liquid, illiquid). |
| **Market Impact** | Price movement caused by executing a large order. |
