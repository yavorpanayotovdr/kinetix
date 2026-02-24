package com.kinetix.referencedata.cache

import com.kinetix.common.model.CreditSpread
import com.kinetix.common.model.DividendYield
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.ReferenceDataSource
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

class RedisReferenceDataCache(
    private val connection: StatefulRedisConnection<String, String>,
) : ReferenceDataCache {

    private val async = connection.async()

    override suspend fun putDividendYield(dividendYield: DividendYield) {
        val key = "dividend-yield:${dividendYield.instrumentId.value}"
        val value = Json.encodeToString(CachedDividendYield.from(dividendYield))
        async.set(key, value).await()
    }

    override suspend fun getDividendYield(instrumentId: InstrumentId): DividendYield? {
        val key = "dividend-yield:${instrumentId.value}"
        val value = async.get(key).await() ?: return null
        return Json.decodeFromString<CachedDividendYield>(value).toDomain()
    }

    override suspend fun putCreditSpread(creditSpread: CreditSpread) {
        val key = "credit-spread:${creditSpread.instrumentId.value}"
        val value = Json.encodeToString(CachedCreditSpread.from(creditSpread))
        async.set(key, value).await()
    }

    override suspend fun getCreditSpread(instrumentId: InstrumentId): CreditSpread? {
        val key = "credit-spread:${instrumentId.value}"
        val value = async.get(key).await() ?: return null
        return Json.decodeFromString<CachedCreditSpread>(value).toDomain()
    }

    @Serializable
    internal data class CachedDividendYield(
        val instrumentId: String,
        val yield: Double,
        val exDate: String?,
        val asOfDate: String,
        val source: String,
    ) {
        companion object {
            fun from(d: DividendYield) = CachedDividendYield(
                instrumentId = d.instrumentId.value,
                yield = d.yield,
                exDate = d.exDate?.toString(),
                asOfDate = d.asOfDate.toString(),
                source = d.source.name,
            )
        }

        fun toDomain() = DividendYield(
            instrumentId = InstrumentId(instrumentId),
            yield = yield,
            exDate = exDate?.let { LocalDate.parse(it) },
            asOfDate = Instant.parse(asOfDate),
            source = ReferenceDataSource.valueOf(source),
        )
    }

    @Serializable
    internal data class CachedCreditSpread(
        val instrumentId: String,
        val spread: Double,
        val rating: String?,
        val asOfDate: String,
        val source: String,
    ) {
        companion object {
            fun from(cs: CreditSpread) = CachedCreditSpread(
                instrumentId = cs.instrumentId.value,
                spread = cs.spread,
                rating = cs.rating,
                asOfDate = cs.asOfDate.toString(),
                source = cs.source.name,
            )
        }

        fun toDomain() = CreditSpread(
            instrumentId = InstrumentId(instrumentId),
            spread = spread,
            rating = rating,
            asOfDate = Instant.parse(asOfDate),
            source = ReferenceDataSource.valueOf(source),
        )
    }
}
