package com.kinetix.risk.cache

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

private fun valuationResult(
    portfolioId: String = "port-1",
    varValue: Double = 5000.0,
) = ValuationResult(
    portfolioId = PortfolioId(portfolioId),
    calculationType = CalculationType.PARAMETRIC,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = varValue,
    expectedShortfall = varValue * 1.25,
    componentBreakdown = listOf(
        ComponentBreakdown(AssetClass.EQUITY, varValue, 100.0),
    ),
    greeks = null,
    calculatedAt = Instant.parse("2025-01-15T10:30:00Z"),
    computedOutputs = setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL),
)

class RedisVaRCacheIntegrationTest : FunSpec({

    val connection = RedisTestSetup.start()
    val cache: VaRCache = RedisVaRCache(connection)

    beforeEach {
        connection.sync().flushall()
    }

    test("should store and retrieve VaR result") {
        val result = valuationResult(portfolioId = "port-1", varValue = 4200.0)

        cache.put("port-1", result)

        val cached = cache.get("port-1")
        cached.shouldNotBeNull()
        cached.portfolioId shouldBe PortfolioId("port-1")
        cached.varValue shouldBe 4200.0
        cached.expectedShortfall shouldBe 4200.0 * 1.25
        cached.calculationType shouldBe CalculationType.PARAMETRIC
        cached.confidenceLevel shouldBe ConfidenceLevel.CL_95
        cached.calculatedAt shouldBe Instant.parse("2025-01-15T10:30:00Z")
    }

    test("should return null for missing portfolio") {
        cache.get("nonexistent").shouldBeNull()
    }

    test("should overwrite existing entry") {
        val first = valuationResult(portfolioId = "port-1", varValue = 1000.0)
        val second = valuationResult(portfolioId = "port-1", varValue = 9999.0)

        cache.put("port-1", first)
        cache.put("port-1", second)

        val cached = cache.get("port-1")
        cached.shouldNotBeNull()
        cached.varValue shouldBe 9999.0
    }

    test("should respect TTL") {
        val shortTtlCache = RedisVaRCache(connection, ttlSeconds = 1L)
        val result = valuationResult()

        shortTtlCache.put("port-1", result)
        shortTtlCache.get("port-1").shouldNotBeNull()

        Thread.sleep(1500)

        shortTtlCache.get("port-1").shouldBeNull()
    }

    test("should cache different portfolios independently") {
        cache.put("port-1", valuationResult(portfolioId = "port-1", varValue = 1000.0))
        cache.put("port-2", valuationResult(portfolioId = "port-2", varValue = 2000.0))

        cache.get("port-1")!!.varValue shouldBe 1000.0
        cache.get("port-2")!!.varValue shouldBe 2000.0
    }

    test("should preserve component breakdown") {
        val result = valuationResult()

        cache.put("port-1", result)

        val cached = cache.get("port-1")!!
        cached.componentBreakdown.size shouldBe 1
        cached.componentBreakdown[0].assetClass shouldBe AssetClass.EQUITY
        cached.componentBreakdown[0].varContribution shouldBe 5000.0
        cached.componentBreakdown[0].percentageOfTotal shouldBe 100.0
    }

    test("should preserve computed outputs") {
        val result = valuationResult()

        cache.put("port-1", result)

        val cached = cache.get("port-1")!!
        cached.computedOutputs shouldBe setOf(ValuationOutput.VAR, ValuationOutput.EXPECTED_SHORTFALL)
    }
})
