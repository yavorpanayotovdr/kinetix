package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RatesServiceClient
import com.kinetix.risk.client.VolatilityServiceClient
import com.kinetix.risk.model.DailyRiskSnapshot
import com.kinetix.risk.model.PnlAttribution
import com.kinetix.risk.persistence.DailyRiskSnapshotRepository
import com.kinetix.risk.persistence.PnlAttributionRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class PnlComputationService(
    private val sodSnapshotService: SodSnapshotService,
    private val dailyRiskSnapshotRepository: DailyRiskSnapshotRepository,
    private val pnlAttributionService: PnlAttributionService,
    private val pnlAttributionRepository: PnlAttributionRepository,
    private val varCache: VaRCache,
    private val positionProvider: PositionProvider,
    private val volatilityServiceClient: VolatilityServiceClient? = null,
    private val ratesServiceClient: RatesServiceClient? = null,
) {
    private val logger = LoggerFactory.getLogger(PnlComputationService::class.java)

    suspend fun compute(
        bookId: BookId,
        date: LocalDate = LocalDate.now(),
    ): PnlAttribution {
        val status = sodSnapshotService.getBaselineStatus(bookId, date)
        if (!status.exists) {
            throw NoSodBaselineException(bookId.value)
        }

        val sodSnapshots = dailyRiskSnapshotRepository.findByBookIdAndDate(bookId, date)
        val currentPositions = positionProvider.getPositions(bookId)

        // Prefetch current market data for all instruments in one pass.
        val instrumentIds = sodSnapshots.map { it.instrumentId }.distinct()
        val currencies = currentPositions.map { it.currency }.distinct()
        val currentVolMap = fetchCurrentVols(instrumentIds)
        val currentRateMap = fetchCurrentRates(currencies)

        val inputs = sodSnapshots.map { snapshot ->
            val currentPosition = currentPositions.find { it.instrumentId == snapshot.instrumentId }
            val currentPrice = currentPosition?.marketPrice?.amount ?: snapshot.marketPrice
            val priceChange = currentPrice.subtract(snapshot.marketPrice)
            val totalPnl = priceChange.multiply(snapshot.quantity)
            val currency = currentPosition?.currency ?: Currency.getInstance("USD")

            val volChange = computeVolChange(snapshot, currentVolMap[snapshot.instrumentId])
            val rateChange = computeRateChange(snapshot, currentRateMap[currency])

            PositionPnlInput(
                instrumentId = snapshot.instrumentId,
                assetClass = snapshot.assetClass,
                totalPnl = totalPnl,
                delta = BigDecimal.valueOf(snapshot.delta ?: 0.0),
                gamma = BigDecimal.valueOf(snapshot.gamma ?: 0.0),
                vega = BigDecimal.valueOf(snapshot.vega ?: 0.0),
                theta = BigDecimal.valueOf(snapshot.theta ?: 0.0),
                rho = BigDecimal.valueOf(snapshot.rho ?: 0.0),
                priceChange = priceChange,
                volChange = volChange,
                rateChange = rateChange,
            )
        }

        val attribution = pnlAttributionService.attribute(bookId, inputs, date)
        pnlAttributionRepository.save(attribution)

        logger.info(
            "P&L attribution computed for portfolio {} on {}: totalPnl={}",
            bookId.value, date, attribution.totalPnl,
        )

        return attribution
    }

    private suspend fun fetchCurrentVols(instrumentIds: List<InstrumentId>): Map<InstrumentId, Double> {
        val client = volatilityServiceClient ?: return emptyMap()
        return instrumentIds.mapNotNull { id ->
            when (val response = client.getLatestSurface(id)) {
                is ClientResponse.Success -> {
                    val atm = response.value.points.firstOrNull()?.impliedVol?.toDouble()
                    if (atm != null) id to atm else null
                }
                is ClientResponse.NotFound -> {
                    logger.debug("No current vol surface for {} — volChange will be zero", id.value)
                    null
                }
            }
        }.toMap()
    }

    private suspend fun fetchCurrentRates(currencies: List<Currency>): Map<Currency, Double> {
        val client = ratesServiceClient ?: return emptyMap()
        return currencies.mapNotNull { currency ->
            when (val response = client.getLatestRiskFreeRate(currency, "1Y")) {
                is ClientResponse.Success -> currency to response.value.rate
                is ClientResponse.NotFound -> {
                    logger.debug("No current risk-free rate for {} 1Y — rateChange will be zero", currency.currencyCode)
                    null
                }
            }
        }.toMap()
    }

    private fun computeVolChange(snapshot: DailyRiskSnapshot, currentVol: Double?): BigDecimal {
        val sodVol = snapshot.sodVol ?: return BigDecimal.ZERO
        val current = currentVol ?: return BigDecimal.ZERO
        return BigDecimal.valueOf(current - sodVol)
    }

    private fun computeRateChange(snapshot: DailyRiskSnapshot, currentRate: Double?): BigDecimal {
        val sodRate = snapshot.sodRate ?: return BigDecimal.ZERO
        val current = currentRate ?: return BigDecimal.ZERO
        return BigDecimal.valueOf(current - sodRate)
    }
}
