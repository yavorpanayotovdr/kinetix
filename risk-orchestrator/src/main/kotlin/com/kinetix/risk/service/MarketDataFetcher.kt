package com.kinetix.risk.service

import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.model.DiscoveredDependency
import com.kinetix.risk.model.MarketDataValue
import com.kinetix.risk.model.ScalarMarketData
import com.kinetix.risk.model.TimeSeriesMarketData
import com.kinetix.risk.model.TimeSeriesPoint
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class MarketDataFetcher(
    private val priceServiceClient: PriceServiceClient,
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

        else -> {
            logger.debug("Cannot fetch {} from price-service, skipping", dataType)
            null
        }
    }
}
