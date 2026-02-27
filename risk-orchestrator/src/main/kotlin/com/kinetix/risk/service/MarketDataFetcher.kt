package com.kinetix.risk.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.client.RatesServiceClient
import com.kinetix.risk.client.ReferenceDataServiceClient
import com.kinetix.risk.client.VolatilityServiceClient
import com.kinetix.risk.client.CorrelationServiceClient
import com.kinetix.risk.model.CurveMarketData
import com.kinetix.risk.model.CurvePointValue
import com.kinetix.risk.model.DiscoveredDependency
import com.kinetix.risk.model.FetchFailure
import com.kinetix.risk.model.FetchResult
import com.kinetix.risk.model.FetchSuccess
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.MatrixMarketData
import com.kinetix.risk.model.ScalarMarketData
import com.kinetix.risk.model.TimeSeriesMarketData
import com.kinetix.risk.model.TimeSeriesPoint
import io.ktor.client.plugins.ResponseException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Currency

class MarketDataFetcher(
    private val priceServiceClient: PriceServiceClient,
    private val ratesServiceClient: RatesServiceClient? = null,
    private val referenceDataServiceClient: ReferenceDataServiceClient? = null,
    private val volatilityServiceClient: VolatilityServiceClient? = null,
    private val correlationServiceClient: CorrelationServiceClient? = null,
    private val priceServiceBaseUrl: String = "",
    private val ratesServiceBaseUrl: String = "",
    private val referenceDataServiceBaseUrl: String = "",
    private val volatilityServiceBaseUrl: String = "",
    private val correlationServiceBaseUrl: String = "",
) {
    private val logger = LoggerFactory.getLogger(MarketDataFetcher::class.java)

    suspend fun fetch(dependencies: List<DiscoveredDependency>): List<FetchResult> {
        val results = mutableListOf<FetchResult>()

        for (dep in dependencies) {
            val startTime = Instant.now()
            try {
                val result = fetchDependency(dep.dataType, dep.instrumentId, dep.assetClass, dep.parameters)
                val durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis()
                when (result) {
                    is ClientResponse.Success -> results.add(FetchSuccess(dep, result.value))
                    is ClientResponse.NotFound -> results.add(
                        FetchFailure(
                            dependency = dep,
                            reason = "NOT_FOUND",
                            url = resolveUrl(dep.dataType, dep.instrumentId, dep.parameters),
                            httpStatus = result.httpStatus,
                            errorMessage = null,
                            service = resolveServiceName(dep.dataType),
                            timestamp = startTime,
                            durationMs = durationMs,
                        )
                    )
                    null -> {
                        val reason = if (isClientAvailable(dep.dataType)) "NOT_FOUND" else "CLIENT_UNAVAILABLE"
                        results.add(
                            FetchFailure(
                                dependency = dep,
                                reason = reason,
                                url = resolveUrl(dep.dataType, dep.instrumentId, dep.parameters),
                                httpStatus = null,
                                errorMessage = null,
                                service = resolveServiceName(dep.dataType),
                                timestamp = startTime,
                                durationMs = durationMs,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                val durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis()
                logger.warn("Failed to fetch {} for {}, skipping", dep.dataType, dep.instrumentId, e)
                results.add(
                    FetchFailure(
                        dependency = dep,
                        reason = "EXCEPTION",
                        url = resolveUrl(dep.dataType, dep.instrumentId, dep.parameters),
                        httpStatus = extractHttpStatus(e),
                        errorMessage = e.message,
                        service = resolveServiceName(dep.dataType),
                        timestamp = startTime,
                        durationMs = durationMs,
                    )
                )
            }
        }

        val successCount = results.count { it is FetchSuccess }
        logger.debug("Fetched {} market data values for {} dependencies", successCount, dependencies.size)
        return results
    }

    private fun isClientAvailable(dataType: String): Boolean = when (dataType) {
        "SPOT_PRICE", "HISTORICAL_PRICES" -> true
        "YIELD_CURVE", "RISK_FREE_RATE", "FORWARD_CURVE" -> ratesServiceClient != null
        "DIVIDEND_YIELD", "CREDIT_SPREAD" -> referenceDataServiceClient != null
        "VOLATILITY_SURFACE" -> volatilityServiceClient != null
        "CORRELATION_MATRIX" -> correlationServiceClient != null
        else -> false
    }

    private fun resolveServiceName(dataType: String): String = when (dataType) {
        "SPOT_PRICE", "HISTORICAL_PRICES" -> "price-service"
        "YIELD_CURVE", "RISK_FREE_RATE", "FORWARD_CURVE" -> "rates-service"
        "DIVIDEND_YIELD", "CREDIT_SPREAD" -> "reference-data-service"
        "VOLATILITY_SURFACE" -> "volatility-service"
        "CORRELATION_MATRIX" -> "correlation-service"
        else -> "unknown"
    }

    private fun resolveUrl(dataType: String, instrumentId: String, parameters: Map<String, String>): String? {
        val id = instrumentId
        return when (dataType) {
            "SPOT_PRICE" -> "$priceServiceBaseUrl/api/prices/$id/latest"
            "HISTORICAL_PRICES" -> "$priceServiceBaseUrl/api/prices/$id/history"
            "YIELD_CURVE" -> {
                val curveId = parameters["curveId"] ?: id
                "$ratesServiceBaseUrl/api/rates/yield-curves/$curveId/latest"
            }
            "RISK_FREE_RATE" -> {
                val currency = parameters["currency"] ?: "USD"
                val tenor = parameters["tenor"] ?: "3M"
                "$ratesServiceBaseUrl/api/rates/risk-free/$currency/$tenor"
            }
            "FORWARD_CURVE" -> "$ratesServiceBaseUrl/api/rates/forward-curves/$id/latest"
            "DIVIDEND_YIELD" -> "$referenceDataServiceBaseUrl/api/reference-data/$id/dividend-yield"
            "CREDIT_SPREAD" -> "$referenceDataServiceBaseUrl/api/reference-data/$id/credit-spread"
            "VOLATILITY_SURFACE" -> "$volatilityServiceBaseUrl/api/volatility/$id/surface/latest"
            "CORRELATION_MATRIX" -> "$correlationServiceBaseUrl/api/correlation/matrix"
            else -> null
        }
    }

    private fun extractHttpStatus(e: Exception): Int? {
        if (e is ResponseException) {
            return e.response.status.value
        }
        return null
    }

    private suspend fun fetchDependency(
        dataType: String,
        instrumentId: String,
        assetClass: String,
        parameters: Map<String, String>,
    ): ClientResponse<MarketDataValue>? = when (dataType) {
        "SPOT_PRICE" -> {
            when (val response = priceServiceClient.getLatestPrice(InstrumentId(instrumentId))) {
                is ClientResponse.Success -> ClientResponse.Success(
                    ScalarMarketData(
                        dataType = "SPOT_PRICE",
                        instrumentId = instrumentId,
                        assetClass = assetClass,
                        value = response.value.price.amount.toDouble(),
                    )
                )
                is ClientResponse.NotFound -> response
            }
        }

        "HISTORICAL_PRICES" -> {
            val lookbackDays = parameters["lookbackDays"]?.toLongOrNull() ?: 252L
            val now = Instant.now()
            val from = now.minus(lookbackDays, ChronoUnit.DAYS)
            when (val response = priceServiceClient.getPriceHistory(InstrumentId(instrumentId), from, now)) {
                is ClientResponse.Success -> {
                    val history = response.value
                    if (history.isNotEmpty()) {
                        ClientResponse.Success(
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
                        )
                    } else null
                }
                is ClientResponse.NotFound -> response
            }
        }

        "YIELD_CURVE" -> {
            val curveId = parameters["curveId"] ?: instrumentId
            val response = ratesServiceClient?.getLatestYieldCurve(curveId)
            when (response) {
                is ClientResponse.Success -> ClientResponse.Success(
                    CurveMarketData(
                        dataType = "YIELD_CURVE",
                        instrumentId = instrumentId,
                        assetClass = assetClass,
                        points = response.value.tenors.map { tenor ->
                            CurvePointValue(tenor = tenor.label, value = tenor.rate.toDouble())
                        },
                    )
                )
                is ClientResponse.NotFound -> response
                null -> null
            }
        }

        "RISK_FREE_RATE" -> {
            val currency = parameters["currency"] ?: "USD"
            val tenor = parameters["tenor"] ?: "3M"
            val response = ratesServiceClient?.getLatestRiskFreeRate(Currency.getInstance(currency), tenor)
            when (response) {
                is ClientResponse.Success -> ClientResponse.Success(
                    ScalarMarketData(
                        dataType = "RISK_FREE_RATE",
                        instrumentId = instrumentId,
                        assetClass = assetClass,
                        value = response.value.rate,
                    )
                )
                is ClientResponse.NotFound -> response
                null -> null
            }
        }

        "FORWARD_CURVE" -> {
            val response = ratesServiceClient?.getLatestForwardCurve(InstrumentId(instrumentId))
            when (response) {
                is ClientResponse.Success -> ClientResponse.Success(
                    CurveMarketData(
                        dataType = "FORWARD_CURVE",
                        instrumentId = instrumentId,
                        assetClass = assetClass,
                        points = response.value.points.map { point ->
                            CurvePointValue(tenor = point.tenor, value = point.value)
                        },
                    )
                )
                is ClientResponse.NotFound -> response
                null -> null
            }
        }

        "DIVIDEND_YIELD" -> {
            val response = referenceDataServiceClient?.getLatestDividendYield(InstrumentId(instrumentId))
            when (response) {
                is ClientResponse.Success -> ClientResponse.Success(
                    ScalarMarketData(
                        dataType = "DIVIDEND_YIELD",
                        instrumentId = instrumentId,
                        assetClass = assetClass,
                        value = response.value.yield,
                    )
                )
                is ClientResponse.NotFound -> response
                null -> null
            }
        }

        "CREDIT_SPREAD" -> {
            val response = referenceDataServiceClient?.getLatestCreditSpread(InstrumentId(instrumentId))
            when (response) {
                is ClientResponse.Success -> ClientResponse.Success(
                    ScalarMarketData(
                        dataType = "CREDIT_SPREAD",
                        instrumentId = instrumentId,
                        assetClass = assetClass,
                        value = response.value.spread,
                    )
                )
                is ClientResponse.NotFound -> response
                null -> null
            }
        }

        "VOLATILITY_SURFACE" -> {
            val response = volatilityServiceClient?.getLatestSurface(InstrumentId(instrumentId))
            when (response) {
                is ClientResponse.Success -> {
                    val surface = response.value
                    val strikes = surface.strikes.map { s -> s.toDouble() }
                    val maturities = surface.maturities.map { m -> m.toString() }
                    val values = maturities.flatMap { mat ->
                        strikes.map { strike ->
                            surface.volAt(strike.toBigDecimal(), mat.toInt()).toDouble()
                        }
                    }
                    ClientResponse.Success(
                        MatrixMarketData(
                            dataType = "VOLATILITY_SURFACE",
                            instrumentId = instrumentId,
                            assetClass = assetClass,
                            rows = maturities,
                            columns = strikes.map { s -> s.toString() },
                            values = values,
                        )
                    )
                }
                is ClientResponse.NotFound -> response
                null -> null
            }
        }

        "CORRELATION_MATRIX" -> {
            val labels = parameters["labels"]?.split(",") ?: emptyList()
            val windowDays = parameters["windowDays"]?.toIntOrNull() ?: 252
            if (labels.isEmpty()) {
                logger.debug("No labels provided for CORRELATION_MATRIX, skipping")
                null
            } else {
                val response = correlationServiceClient?.getCorrelationMatrix(labels, windowDays)
                when (response) {
                    is ClientResponse.Success -> ClientResponse.Success(
                        MatrixMarketData(
                            dataType = "CORRELATION_MATRIX",
                            instrumentId = instrumentId,
                            assetClass = assetClass,
                            rows = response.value.labels,
                            columns = response.value.labels,
                            values = response.value.values,
                        )
                    )
                    is ClientResponse.NotFound -> response
                    null -> null
                }
            }
        }

        else -> {
            logger.debug("Cannot fetch {} for {}, skipping", dataType, instrumentId)
            null
        }
    }
}
