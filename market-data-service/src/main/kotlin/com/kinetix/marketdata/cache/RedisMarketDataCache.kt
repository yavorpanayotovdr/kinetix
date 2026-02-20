package com.kinetix.marketdata.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.MarketDataPoint
import com.kinetix.common.model.MarketDataSource
import com.kinetix.common.model.Money
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

class RedisMarketDataCache(
    private val connection: StatefulRedisConnection<String, String>,
) : MarketDataCache {

    private val async = connection.async()

    override suspend fun put(point: MarketDataPoint) {
        val key = keyFor(point.instrumentId)
        val value = Json.encodeToString(CachedPrice.from(point))
        async.set(key, value).await()
    }

    override suspend fun get(instrumentId: InstrumentId): MarketDataPoint? {
        val key = keyFor(instrumentId)
        val value = async.get(key).await() ?: return null
        val cached = Json.decodeFromString<CachedPrice>(value)
        return cached.toMarketDataPoint()
    }

    private fun keyFor(instrumentId: InstrumentId): String =
        "market:price:${instrumentId.value}"

    @Serializable
    internal data class CachedPrice(
        val instrumentId: String,
        val priceAmount: String,
        val priceCurrency: String,
        val timestamp: String,
        val source: String,
    ) {
        fun toMarketDataPoint(): MarketDataPoint = MarketDataPoint(
            instrumentId = InstrumentId(instrumentId),
            price = Money(BigDecimal(priceAmount), Currency.getInstance(priceCurrency)),
            timestamp = Instant.parse(timestamp),
            source = MarketDataSource.valueOf(source),
        )

        companion object {
            fun from(point: MarketDataPoint): CachedPrice = CachedPrice(
                instrumentId = point.instrumentId.value,
                priceAmount = point.price.amount.toPlainString(),
                priceCurrency = point.price.currency.currencyCode,
                timestamp = point.timestamp.toString(),
                source = point.source.name,
            )
        }
    }
}
