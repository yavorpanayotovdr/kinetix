package com.kinetix.risk.cache

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.BookId
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant

private fun minimalValuationResult(bookId: String = "port-1") = ValuationResult(
    bookId = BookId(bookId),
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = 5000.0,
    expectedShortfall = 6250.0,
    componentBreakdown = emptyList(),
    greeks = null,
    calculatedAt = Instant.parse("2025-01-15T10:30:00Z"),
    computedOutputs = setOf(ValuationOutput.VAR),
    pvValue = null,
    positionRisk = emptyList(),
    jobId = null,
)

class RedisVaRCacheTest : FunSpec({

    test("cache key includes schema version") {
        val key = "var:v${RedisVaRCache.CACHE_SCHEMA_VERSION}:port-1"
        key shouldBe "var:v1:port-1"
    }

    test("deserialization with ignoreUnknownKeys tolerates extra fields") {
        val jsonWithExtraField = """
            {
                "bookId": "port-1",
                "calculationType": "PARAMETRIC",
                "confidenceLevel": "CL_95",
                "varValue": 5000.0,
                "expectedShortfall": 6250.0,
                "componentBreakdown": [],
                "greeks": null,
                "calculatedAt": "2025-01-15T10:30:00Z",
                "computedOutputs": ["VAR"],
                "pvValue": null,
                "positionRisk": [],
                "jobId": null,
                "futureField": "should be ignored"
            }
        """.trimIndent()

        val cacheJson = Json { ignoreUnknownKeys = true }
        val result = cacheJson.decodeFromString<CachedValuationResult>(jsonWithExtraField)

        result.bookId shouldBe "port-1"
        result.varValue shouldBe 5000.0
    }

    test("deserialization without ignoreUnknownKeys fails on extra fields") {
        val jsonWithExtraField = """
            {
                "bookId": "port-1",
                "calculationType": "PARAMETRIC",
                "confidenceLevel": "CL_95",
                "varValue": 5000.0,
                "expectedShortfall": 6250.0,
                "componentBreakdown": [],
                "greeks": null,
                "calculatedAt": "2025-01-15T10:30:00Z",
                "computedOutputs": ["VAR"],
                "pvValue": null,
                "positionRisk": [],
                "jobId": null,
                "futureField": "should cause error"
            }
        """.trimIndent()

        val strictJson = Json { ignoreUnknownKeys = false }
        val result = runCatching {
            strictJson.decodeFromString<CachedValuationResult>(jsonWithExtraField)
        }

        result.isFailure shouldBe true
        result.exceptionOrNull()!!.message shouldContain "futureField"
    }

    test("CACHE_SCHEMA_VERSION is a positive integer") {
        (RedisVaRCache.CACHE_SCHEMA_VERSION > 0) shouldBe true
    }

    test("marketDataComplete defaults to true when absent in cached JSON") {
        val jsonWithoutFlag = """
            {
                "bookId": "port-1",
                "calculationType": "PARAMETRIC",
                "confidenceLevel": "CL_95",
                "varValue": 5000.0,
                "expectedShortfall": 6250.0,
                "componentBreakdown": [],
                "greeks": null,
                "calculatedAt": "2025-01-15T10:30:00Z",
                "computedOutputs": ["VAR"],
                "pvValue": null,
                "positionRisk": [],
                "jobId": null
            }
        """.trimIndent()

        val cacheJson = Json { ignoreUnknownKeys = true }
        val result = cacheJson.decodeFromString<CachedValuationResult>(jsonWithoutFlag)

        result.marketDataComplete shouldBe true
    }

    test("marketDataComplete is preserved when false") {
        val jsonWithFalse = """
            {
                "bookId": "port-1",
                "calculationType": "PARAMETRIC",
                "confidenceLevel": "CL_95",
                "varValue": 4000.0,
                "expectedShortfall": 5000.0,
                "componentBreakdown": [],
                "greeks": null,
                "calculatedAt": "2025-01-15T10:30:00Z",
                "computedOutputs": ["VAR"],
                "pvValue": null,
                "positionRisk": [],
                "jobId": null,
                "marketDataComplete": false
            }
        """.trimIndent()

        val cacheJson = Json { ignoreUnknownKeys = true }
        val result = cacheJson.decodeFromString<CachedValuationResult>(jsonWithFalse)

        result.marketDataComplete shouldBe false
    }

    test("put completes normally when Redis throws RedisConnectionException") {
        val commands = mockk<RedisCommands<String, String>>()
        val connection = mockk<StatefulRedisConnection<String, String>>()
        every { connection.sync() } returns commands
        every { commands.set(any(), any(), any()) } throws RedisConnectionException("connection refused")

        val cache = RedisVaRCache(connection)

        // Must not throw
        cache.put("port-1", minimalValuationResult())
    }

    test("get returns null when Redis throws RedisConnectionException") {
        val commands = mockk<RedisCommands<String, String>>()
        val connection = mockk<StatefulRedisConnection<String, String>>()
        every { connection.sync() } returns commands
        every { commands.get(any()) } throws RedisConnectionException("connection refused")

        val cache = RedisVaRCache(connection)

        val result = cache.get("port-1")

        result shouldBe null
    }
})
