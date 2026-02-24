package com.kinetix.risk.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.client.RatesServiceClient
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.VolatilityServiceClient
import com.kinetix.risk.model.CurveMarketData
import com.kinetix.risk.model.CurvePointValue
import com.kinetix.risk.model.DiscoveredDependency
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.MatrixMarketData
import com.kinetix.risk.model.ScalarMarketData
import com.kinetix.risk.model.TimeSeriesMarketData
import com.kinetix.risk.model.TimeSeriesPoint
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Currency

class MarketDataFetcher(
    private val priceServiceClient: PriceServiceClient,
    private val ratesServiceClient: RatesServiceClient? = null,
    private val referenceDataServiceClient: ReferenceDataServiceClient? = null,
    private val volatilityServiceClient: VolatilityServiceClient? = null,
) {
    private val logger = LoggerFactory.getLogger(MarketDataFetcher::class.java)

    suspend fun fetch(dependencies: List<DiscoveredDependency>): List<MarketDataValue> {
        val results = mutableListOf<MarketDataValue>()

        for (dep in dependencies) {
            try {
                val value = fetchDependency(dep.dataType, dep.instrumentId, dep.assetClass, dep.parameters)
                if (value != null) {
                    results.add(value)
                }
            } catch (e: Exception) {
                logger.warn("Failed to fetch {} for {}, skipping", dep.dataType, dep.instrumentId, e)
            }
        }

        logger.debug("Fetched {} market data values for {} dependencies", results.size, dependencies.size)
        return results
    }

    private suspend fun fetchDependency(
        dataType: String,
        instrumentId: String,
        assetClass: String,
        parameters: Map<String, String>,
    ): MarketDataValue? = when (dataType) {
        "SPOT_PRICE" -> {
            val pricePoint = priceServiceClient.getLatestPrice(InstrumentId(instrumentId))
            pricePoint?.let {
                ScalarMarketData(
                    dataType = "SPOT_PRICE",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    value = it.price.amount.toDouble(),
                )
            }
        }

        "HISTORICAL_PRICES" -> {
            val lookbackDays = parameters["lookbackDays"]?.toLongOrNull() ?: 252L
            val now = Instant.now()
            val from = now.minus(lookbackDays, ChronoUnit.DAYS)
            val history = priceServiceClient.getPriceHistory(InstrumentId(instrumentId), from, now)
            if (history.isNotEmpty()) {
                TimeSeriesMarketData(
                    dataType = "HISTORICAL_PRICES",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    points = history.map { pp ->
                        TimeSeriesPoint(
                            timestamp = pp.timestamp,
                            value = pp.price.amount.toDouble(),
                        )
                    },
                )
            } else null
        }

        "YIELD_CURVE" -> {
            val curveId = parameters["curveId"] ?: instrumentId
            val yieldCurve = ratesServiceClient?.getLatestYieldCurve(curveId)
            yieldCurve?.let {
                CurveMarketData(
                    dataType = "YIELD_CURVE",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    points = it.tenors.map { tenor ->
                        CurvePointValue(tenor = tenor.label, value = tenor.rate.toDouble())
                    },
                )
            }
        }

        "RISK_FREE_RATE" -> {
            val currency = parameters["currency"] ?: "USD"
            val tenor = parameters["tenor"] ?: "3M"
            val riskFreeRate = ratesServiceClient?.getLatestRiskFreeRate(Currency.getInstance(currency), tenor)
            riskFreeRate?.let {
                ScalarMarketData(
                    dataType = "RISK_FREE_RATE",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    value = it.rate,
                )
            }
        }

        "FORWARD_CURVE" -> {
            val forwardCurve = ratesServiceClient?.getLatestForwardCurve(InstrumentId(instrumentId))
            forwardCurve?.let {
                CurveMarketData(
                    dataType = "FORWARD_CURVE",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    points = it.points.map { point ->
                        CurvePointValue(tenor = point.tenor, value = point.value)
                    },
                )
            }
        }

        "DIVIDEND_YIELD" -> {
            val dividendYield = referenceDataServiceClient?.getLatestDividendYield(InstrumentId(instrumentId))
            dividendYield?.let {
                ScalarMarketData(
                    dataType = "DIVIDEND_YIELD",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    value = it.yield,
                )
            }
        }

        "CREDIT_SPREAD" -> {
            val creditSpread = referenceDataServiceClient?.getLatestCreditSpread(InstrumentId(instrumentId))
            creditSpread?.let {
                ScalarMarketData(
                    dataType = "CREDIT_SPREAD",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    value = it.spread,
                )
            }
        }

        "VOLATILITY_SURFACE" -> {
            val surface = volatilityServiceClient?.getLatestSurface(InstrumentId(instrumentId))
            surface?.let {
                val strikes = it.strikes.map { s -> s.toDouble() }
                val maturities = it.maturities.map { m -> m.toString() }
                val values = maturities.flatMap { mat ->
                    strikes.map { strike ->
                        it.volAt(strike.toBigDecimal(), mat.toInt()).toDouble()
                    }
                }
                MatrixMarketData(
                    dataType = "VOLATILITY_SURFACE",
                    instrumentId = instrumentId,
                    assetClass = assetClass,
                    rows = maturities,
                    columns = strikes.map { s -> s.toString() },
                    values = values,
                )
            }
        }

        else -> {
            logger.debug("Cannot fetch {} for {}, skipping", dataType, instrumentId)
            null
        }
    }
}
