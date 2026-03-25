package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RatesServiceClient
import com.kinetix.risk.client.VolatilityServiceClient
import com.kinetix.risk.model.DailyRiskSnapshot
import com.kinetix.risk.model.InstrumentPnlBreakdown
import com.kinetix.risk.model.IntradayPnlSnapshot
import com.kinetix.risk.model.PnlTrigger
import com.kinetix.risk.persistence.DailyRiskSnapshotRepository
import com.kinetix.risk.persistence.IntradayPnlRepository
import com.kinetix.risk.persistence.SodBaselineRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Currency

class IntradayPnlService(
    private val sodBaselineRepository: SodBaselineRepository,
    private val dailyRiskSnapshotRepository: DailyRiskSnapshotRepository,
    private val intradayPnlRepository: IntradayPnlRepository,
    private val positionProvider: PositionProvider,
    private val pnlAttributionService: PnlAttributionService,
    private val publisher: IntradayPnlPublisher,
    private val debounceInterval: Duration = Duration.ofSeconds(1),
    private val volatilityServiceClient: VolatilityServiceClient? = null,
    private val ratesServiceClient: RatesServiceClient? = null,
) {
    private val logger = LoggerFactory.getLogger(IntradayPnlService::class.java)
    private val mc = MathContext(20, RoundingMode.HALF_UP)

    /**
     * Recomputes intraday P&L for [bookId] triggered by a position change.
     *
     * Returns null (without publishing) if:
     * - no SOD baseline exists for the book today
     * - the debounce interval has not elapsed since the last snapshot
     *
     * Otherwise computes total P&L from position state (the truth),
     * attributes it against frozen SOD Greeks, updates the high-water mark,
     * persists the snapshot, and publishes it to Kafka.
     */
    suspend fun recompute(
        bookId: BookId,
        trigger: PnlTrigger,
        correlationId: String?,
        date: LocalDate = LocalDate.now(),
    ): IntradayPnlSnapshot? {
        val baseline = sodBaselineRepository.findByBookIdAndDate(bookId, date)
        if (baseline == null) {
            logger.warn(
                "No SOD baseline for book {} on {} — intraday P&L is undefined",
                bookId.value, date,
            )
            return null
        }

        val lastSnapshot = intradayPnlRepository.findLatest(bookId)
        if (lastSnapshot != null) {
            val elapsed = Duration.between(lastSnapshot.snapshotAt, Instant.now())
            if (elapsed < debounceInterval) {
                return null
            }
        }

        val sodSnapshots = dailyRiskSnapshotRepository.findByBookIdAndDate(bookId, date)
        val positions = positionProvider.getPositions(bookId)

        // Total P&L is the truth: computed from position state, never from Greek attribution.
        val baseCurrency = deriveBaseCurrency(positions)
        val totalRealised = positions.fold(BigDecimal.ZERO) { acc, pos ->
            acc.add(convertToBase(pos.realizedPnl, baseCurrency), mc)
        }
        val totalUnrealised = positions.fold(BigDecimal.ZERO) { acc, pos ->
            acc.add(convertToBase(pos.unrealizedPnl, baseCurrency), mc)
        }
        val totalPnl = totalRealised.add(totalUnrealised, mc)

        // Fetch current market data for vol/rate change computation.
        val instrumentIds = sodSnapshots.map { it.instrumentId }.distinct()
        val currencies = positions.map { it.currency }.distinct()
        val currentVolMap = fetchCurrentVols(instrumentIds)
        val currentRateMap = fetchCurrentRates(currencies)

        // Greek attribution: analytical overlay against frozen SOD state.
        val pnlInputs = buildAttributionInputs(positions, sodSnapshots, baseCurrency, currentVolMap, currentRateMap)
        val attribution = pnlAttributionService.attribute(bookId, pnlInputs, date)

        // High-water mark is monotonically non-decreasing within the trading day.
        val previousHwm = lastSnapshot?.highWaterMark ?: totalPnl
        val newHwm = previousHwm.max(totalPnl)

        val instrumentPnl = attribution.positionAttributions.map { pos ->
            InstrumentPnlBreakdown(
                instrumentId = pos.instrumentId.value,
                assetClass = pos.assetClass.name,
                totalPnl = pos.totalPnl.toPlainString(),
                deltaPnl = pos.deltaPnl.toPlainString(),
                gammaPnl = pos.gammaPnl.toPlainString(),
                vegaPnl = pos.vegaPnl.toPlainString(),
                thetaPnl = pos.thetaPnl.toPlainString(),
                rhoPnl = pos.rhoPnl.toPlainString(),
                unexplainedPnl = pos.unexplainedPnl.toPlainString(),
            )
        }

        val snapshot = IntradayPnlSnapshot(
            bookId = bookId,
            snapshotAt = Instant.now(),
            baseCurrency = baseCurrency,
            trigger = trigger,
            totalPnl = totalPnl,
            realisedPnl = totalRealised,
            unrealisedPnl = totalUnrealised,
            deltaPnl = attribution.deltaPnl,
            gammaPnl = attribution.gammaPnl,
            vegaPnl = attribution.vegaPnl,
            thetaPnl = attribution.thetaPnl,
            rhoPnl = attribution.rhoPnl,
            unexplainedPnl = totalPnl - (attribution.deltaPnl + attribution.gammaPnl +
                attribution.vegaPnl + attribution.thetaPnl + attribution.rhoPnl),
            highWaterMark = newHwm,
            instrumentPnl = instrumentPnl,
            correlationId = correlationId,
        )

        intradayPnlRepository.save(snapshot)
        publisher.publish(snapshot)

        return snapshot
    }

    private fun buildAttributionInputs(
        positions: List<com.kinetix.common.model.Position>,
        sodSnapshots: List<DailyRiskSnapshot>,
        baseCurrency: String,
        currentVolMap: Map<InstrumentId, Double>,
        currentRateMap: Map<Currency, Double>,
    ): List<PositionPnlInput> {
        val sodByInstrument = sodSnapshots.associateBy { it.instrumentId }
        return positions.mapNotNull { position ->
            val sod = sodByInstrument[position.instrumentId] ?: return@mapNotNull null
            val currentPrice = position.marketPrice.amount
            val priceChange = currentPrice.subtract(sod.marketPrice, mc)
            val positionPnl = convertToBase(position.unrealizedPnl, baseCurrency)
                .add(convertToBase(position.realizedPnl, baseCurrency), mc)

            val volChange = computeVolChange(sod, currentVolMap[position.instrumentId])
            val rateChange = computeRateChange(sod, currentRateMap[position.currency])

            PositionPnlInput(
                instrumentId = position.instrumentId,
                assetClass = position.assetClass,
                totalPnl = positionPnl,
                delta = BigDecimal.valueOf(sod.delta ?: 0.0),
                gamma = BigDecimal.valueOf(sod.gamma ?: 0.0),
                vega = BigDecimal.valueOf(sod.vega ?: 0.0),
                theta = BigDecimal.valueOf(sod.theta ?: 0.0),
                rho = BigDecimal.valueOf(sod.rho ?: 0.0),
                priceChange = priceChange,
                volChange = volChange,
                rateChange = rateChange,
            )
        }
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

    private fun convertToBase(money: Money, baseCurrency: String): BigDecimal {
        return if (money.currency.currencyCode == baseCurrency) {
            money.amount
        } else {
            // Simplified: use 1:1 rate when no FX provider is wired.
            // Production improvement: inject LiveFxRateProvider.
            money.amount
        }
    }

    private fun deriveBaseCurrency(positions: List<com.kinetix.common.model.Position>): String {
        return positions.firstOrNull()?.currency?.currencyCode ?: "USD"
    }
}
