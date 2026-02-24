package com.kinetix.correlation.cache

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class RedisCorrelationCache(
    private val connection: StatefulRedisConnection<String, String>,
) : CorrelationCache {

    private val async = connection.async()

    override suspend fun put(labels: List<String>, windowDays: Int, matrix: CorrelationMatrix) {
        val key = buildKey(labels, windowDays)
        val value = Json.encodeToString(CachedCorrelationMatrix.from(matrix))
        async.set(key, value).await()
    }

    override suspend fun get(labels: List<String>, windowDays: Int): CorrelationMatrix? {
        val key = buildKey(labels, windowDays)
        val value = async.get(key).await() ?: return null
        return Json.decodeFromString<CachedCorrelationMatrix>(value).toDomain()
    }

    private fun buildKey(labels: List<String>, windowDays: Int): String {
        val sortedLabels = labels.sorted().joinToString(",")
        return "correlation:$sortedLabels:$windowDays"
    }

    @Serializable
    internal data class CachedCorrelationMatrix(
        val labels: List<String>,
        val values: List<Double>,
        val windowDays: Int,
        val asOfDate: String,
        val method: String,
    ) {
        companion object {
            fun from(m: CorrelationMatrix) = CachedCorrelationMatrix(
                labels = m.labels,
                values = m.values,
                windowDays = m.windowDays,
                asOfDate = m.asOfDate.toString(),
                method = m.method.name,
            )
        }

        fun toDomain() = CorrelationMatrix(
            labels = labels,
            values = values,
            windowDays = windowDays,
            asOfDate = Instant.parse(asOfDate),
            method = EstimationMethod.valueOf(method),
        )
    }
}
