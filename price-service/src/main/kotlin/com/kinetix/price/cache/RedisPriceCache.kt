package com.kinetix.price.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PricePoint
import com.kinetix.common.model.PriceSource
import com.kinetix.common.model.Money
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

class RedisPriceCache(
    private val connection: StatefulRedisConnection<String, String>,
) : PriceCache {

    private val async = connection.async()

    override suspend fun put(point: PricePoint) {
        val key = keyFor(point.instrumentId)
        val value = Json.encodeToString(CachedPrice.from(point))
        async.set(key, value).await()
    }

    override suspend fun get(instrumentId: InstrumentId): PricePoint? {
        val key = keyFor(instrumentId)
        val value = async.get(key).await() ?: return null
        val cached = Json.decodeFromString<CachedPrice>(value)
        return cached.toPricePoint()
    }

    private fun keyFor(instrumentId: InstrumentId): String =
        "price:${instrumentId.value}"

    @Serializable
    internal data class CachedPrice(
        val instrumentId: String,
        val priceAmount: String,
        val priceCurrency: String,
        val timestamp: String,
        val source: String,
    ) {
        fun toPricePoint(): PricePoint = PricePoint(
            instrumentId = InstrumentId(instrumentId),
            price = Money(BigDecimal(priceAmount), Currency.getInstance(priceCurrency)),
            timestamp = Instant.parse(timestamp),
            source = PriceSource.valueOf(source),
        )

        companion object {
            fun from(point: PricePoint): CachedPrice = CachedPrice(
                instrumentId = point.instrumentId.value,
                priceAmount = point.price.amount.toPlainString(),
                priceCurrency = point.price.currency.currencyCode,
                timestamp = point.timestamp.toString(),
                source = point.source.name,
            )
        }
    }
}
