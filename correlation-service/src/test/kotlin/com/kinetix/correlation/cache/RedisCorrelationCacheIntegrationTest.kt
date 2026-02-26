package com.kinetix.correlation.cache

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

class RedisCorrelationCacheIntegrationTest : FunSpec({

    val connection = RedisTestSetup.start()
    val cache: CorrelationCache = RedisCorrelationCache(connection)

    beforeEach {
        connection.sync().flushall()
    }

    test("put and get correlation matrix") {
        val matrix = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.65, 0.65, 1.0),
            windowDays = 252,
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            method = EstimationMethod.HISTORICAL,
        )
        cache.put(listOf("AAPL", "MSFT"), 252, matrix)

        val found = cache.get(listOf("AAPL", "MSFT"), 252)
        found.shouldNotBeNull()
        found.labels shouldBe listOf("AAPL", "MSFT")
        found.values shouldBe listOf(1.0, 0.65, 0.65, 1.0)
        found.method shouldBe EstimationMethod.HISTORICAL
    }

    test("get returns null for unknown labels") {
        cache.get(listOf("X", "Y"), 252).shouldBeNull()
    }

    test("put overwrites previous matrix") {
        val matrix1 = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.5, 0.5, 1.0),
            windowDays = 252,
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            method = EstimationMethod.HISTORICAL,
        )
        cache.put(listOf("AAPL", "MSFT"), 252, matrix1)

        val matrix2 = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.7, 0.7, 1.0),
            windowDays = 252,
            asOfDate = Instant.parse("2026-01-16T10:00:00Z"),
            method = EstimationMethod.EXPONENTIALLY_WEIGHTED,
        )
        cache.put(listOf("AAPL", "MSFT"), 252, matrix2)

        val found = cache.get(listOf("AAPL", "MSFT"), 252)
        found.shouldNotBeNull()
        found.values shouldBe listOf(1.0, 0.7, 0.7, 1.0)
        found.method shouldBe EstimationMethod.EXPONENTIALLY_WEIGHTED
    }

    test("different window days are cached independently") {
        val matrix252 = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.65, 0.65, 1.0),
            windowDays = 252,
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            method = EstimationMethod.HISTORICAL,
        )
        val matrix60 = CorrelationMatrix(
            labels = listOf("AAPL", "MSFT"),
            values = listOf(1.0, 0.8, 0.8, 1.0),
            windowDays = 60,
            asOfDate = Instant.parse("2026-01-15T10:00:00Z"),
            method = EstimationMethod.HISTORICAL,
        )
        cache.put(listOf("AAPL", "MSFT"), 252, matrix252)
        cache.put(listOf("AAPL", "MSFT"), 60, matrix60)

        val found252 = cache.get(listOf("AAPL", "MSFT"), 252)
        val found60 = cache.get(listOf("AAPL", "MSFT"), 60)
        found252.shouldNotBeNull()
        found60.shouldNotBeNull()
        found252.values shouldBe listOf(1.0, 0.65, 0.65, 1.0)
        found60.values shouldBe listOf(1.0, 0.8, 0.8, 1.0)
    }
})
