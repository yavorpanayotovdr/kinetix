package com.kinetix.volatility.cache

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolPoint
import com.kinetix.common.model.VolSurface
import com.kinetix.common.model.VolatilitySource
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant

class RedisVolatilityCache(
    private val connection: StatefulRedisConnection<String, String>,
) : VolatilityCache {

    private val async = connection.async()

    override suspend fun putSurface(surface: VolSurface) {
        val key = "vol-surface:${surface.instrumentId.value}"
        val value = Json.encodeToString(CachedVolSurface.from(surface))
        async.set(key, value).await()
    }

    override suspend fun getSurface(instrumentId: InstrumentId): VolSurface? {
        val key = "vol-surface:${instrumentId.value}"
        val value = async.get(key).await() ?: return null
        return Json.decodeFromString<CachedVolSurface>(value).toDomain()
    }

    @Serializable
    internal data class CachedVolPoint(
        val strike: Double,
        val maturityDays: Int,
        val impliedVol: Double,
    ) {
        companion object {
            fun from(p: VolPoint) = CachedVolPoint(
                strike = p.strike.toDouble(),
                maturityDays = p.maturityDays,
                impliedVol = p.impliedVol.toDouble(),
            )
        }

        fun toDomain() = VolPoint(
            strike = BigDecimal(strike.toString()),
            maturityDays = maturityDays,
            impliedVol = BigDecimal(impliedVol.toString()),
        )
    }

    @Serializable
    internal data class CachedVolSurface(
        val instrumentId: String,
        val asOf: String,
        val points: List<CachedVolPoint>,
        val source: String,
    ) {
        companion object {
            fun from(s: VolSurface) = CachedVolSurface(
                instrumentId = s.instrumentId.value,
                asOf = s.asOf.toString(),
                points = s.points.map { CachedVolPoint.from(it) },
                source = s.source.name,
            )
        }

        fun toDomain() = VolSurface(
            instrumentId = InstrumentId(instrumentId),
            asOf = Instant.parse(asOf),
            points = points.map { it.toDomain() },
            source = VolatilitySource.valueOf(source),
        )
    }
}
