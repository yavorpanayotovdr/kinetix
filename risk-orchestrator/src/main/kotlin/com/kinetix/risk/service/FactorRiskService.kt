package com.kinetix.risk.service

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Position
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.PriceServiceClient
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.kafka.FactorConcentrationAlertPublisher
import com.kinetix.risk.model.FactorDecompositionSnapshot
import com.kinetix.risk.model.TimeSeriesMarketData
import com.kinetix.risk.model.TimeSeriesPoint
import com.kinetix.risk.persistence.FactorDecompositionRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Instrument ids of the 5 factor proxy time series that must be fetched. */
private val FACTOR_PROXY_INSTRUMENT_IDS = listOf("IDX-SPX", "US10Y", "CDX-IG", "EURUSD", "VIX")

/** Number of calendar days of price history to request for OLS regression. */
private const val PRICE_HISTORY_DAYS = 400L

class FactorRiskService(
    private val riskEngineClient: RiskEngineClient,
    private val repository: FactorDecompositionRepository,
    private val positionProvider: PositionProvider? = null,
    private val priceServiceClient: PriceServiceClient? = null,
    private val concentrationAlertPublisher: FactorConcentrationAlertPublisher? = null,
) {
    private val logger = LoggerFactory.getLogger(FactorRiskService::class.java)

    /**
     * Fetches positions and historical prices for [bookId], then runs factor risk
     * decomposition and persists the result.
     *
     * This is the entry point used by the scheduler. It is a no-op when:
     * - [positionProvider] or [priceServiceClient] are not configured
     * - the book has no positions
     * - the gRPC call to the risk engine fails (logged as a warning)
     *
     * @param totalVar the total portfolio VaR from the preceding valuation run.
     */
    suspend fun decomposeForBook(bookId: BookId, totalVar: Double): FactorDecompositionSnapshot? {
        val pp = positionProvider ?: run {
            logger.debug("No PositionProvider configured, skipping factor decomposition for {}", bookId.value)
            return null
        }
        val pc = priceServiceClient ?: run {
            logger.debug("No PriceServiceClient configured, skipping factor decomposition for {}", bookId.value)
            return null
        }

        val positions = pp.getPositions(bookId)
        if (positions.isEmpty()) {
            logger.info("No positions for book {}, skipping factor decomposition", bookId.value)
            return null
        }

        val marketData = fetchPriceHistory(positions, pc)
        return decompose(bookId, positions, marketData, totalVar)
    }

    /**
     * Runs factor risk decomposition using pre-fetched [marketData]. Useful when
     * historical prices are already in memory (e.g. in tests or future pipeline integration).
     */
    suspend fun decompose(
        bookId: BookId,
        positions: List<Position>,
        marketData: Map<String, TimeSeriesMarketData>,
        totalVar: Double,
    ): FactorDecompositionSnapshot? {
        if (positions.isEmpty()) {
            logger.info("No positions for book {}, skipping factor decomposition", bookId.value)
            return null
        }

        val snapshot = try {
            riskEngineClient.decomposeFactorRisk(bookId, positions, marketData, totalVar)
        } catch (e: Exception) {
            logger.warn(
                "Factor risk decomposition failed for book {}, skipping persistence",
                bookId.value, e,
            )
            return null
        }

        repository.save(snapshot)

        if (snapshot.concentrationWarning) {
            try {
                concentrationAlertPublisher?.publishConcentrationWarning(snapshot)
            } catch (e: Exception) {
                logger.warn(
                    "Failed to publish factor concentration alert for book {}: {}",
                    bookId.value, e.message,
                )
            }
        }

        logger.info(
            "Factor decomposition complete for book {}: totalVar={}, systematicVar={}, " +
                "idiosyncraticVar={}, rSquared={}, concentrationWarning={}",
            bookId.value, snapshot.totalVar, snapshot.systematicVar,
            snapshot.idiosyncraticVar, snapshot.rSquared, snapshot.concentrationWarning,
        )

        return snapshot
    }

    private suspend fun fetchPriceHistory(
        positions: List<Position>,
        priceClient: PriceServiceClient,
    ): Map<String, TimeSeriesMarketData> {
        val to = Instant.now()
        val from = to.minus(PRICE_HISTORY_DAYS, ChronoUnit.DAYS)

        val instrumentIds = (
            positions.map { it.instrumentId.value } +
                FACTOR_PROXY_INSTRUMENT_IDS
        ).distinct()

        val result = mutableMapOf<String, TimeSeriesMarketData>()
        for (instrumentIdStr in instrumentIds) {
            val instrumentId = InstrumentId(instrumentIdStr)
            val assetClass = positions.find { it.instrumentId.value == instrumentIdStr }
                ?.assetClass?.name ?: "EQUITY"

            val response = try {
                priceClient.getPriceHistory(instrumentId, from, to)
            } catch (e: Exception) {
                logger.warn("Failed to fetch price history for {}, skipping", instrumentIdStr, e)
                continue
            }

            when (response) {
                is ClientResponse.Success -> {
                    if (response.value.isNotEmpty()) {
                        result[instrumentIdStr] = TimeSeriesMarketData(
                            dataType = "HISTORICAL_PRICES",
                            instrumentId = instrumentIdStr,
                            assetClass = assetClass,
                            points = response.value.map { pp ->
                                TimeSeriesPoint(timestamp = pp.timestamp, value = pp.price.amount.toDouble())
                            },
                        )
                    }
                }
                else -> logger.debug("No price history for {}", instrumentIdStr)
            }
        }
        return result
    }
}
