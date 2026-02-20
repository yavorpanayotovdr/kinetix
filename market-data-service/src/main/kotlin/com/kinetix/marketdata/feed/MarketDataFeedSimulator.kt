package com.kinetix.marketdata.feed

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.common.model.MarketDataSource
import com.kinetix.common.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Currency
import kotlin.random.Random

data class InstrumentSeed(
    val instrumentId: InstrumentId,
    val initialPrice: BigDecimal,
    val currency: Currency,
)

class MarketDataFeedSimulator(
    seeds: List<InstrumentSeed>,
    private val maxChangePercent: Double = 2.0,
    private val random: Random = Random.Default,
) {
    private val currentPrices: MutableMap<InstrumentId, BigDecimal> = seeds.associate {
        it.instrumentId to it.initialPrice
    }.toMutableMap()

    private val currencies: Map<InstrumentId, Currency> = seeds.associate {
        it.instrumentId to it.currency
    }

    fun tick(timestamp: Instant, source: MarketDataSource): List<MarketDataPoint> =
        currentPrices.keys.map { instrumentId ->
            val currentPrice = currentPrices.getValue(instrumentId)
            val changePct = (random.nextDouble() * 2 - 1) * maxChangePercent / 100.0
            val newPrice = (currentPrice * (BigDecimal.ONE + BigDecimal.valueOf(changePct)))
                .max(BigDecimal("0.01"))
                .setScale(currentPrice.scale(), RoundingMode.HALF_UP)
            currentPrices[instrumentId] = newPrice

            MarketDataPoint(
                instrumentId = instrumentId,
                price = Money(newPrice, currencies.getValue(instrumentId)),
                timestamp = timestamp,
                source = source,
            )
        }
}
