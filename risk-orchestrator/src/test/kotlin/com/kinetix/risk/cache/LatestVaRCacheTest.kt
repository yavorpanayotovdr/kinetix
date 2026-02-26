package com.kinetix.risk.cache

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
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

class LatestVaRCacheTest : FunSpec({

    test("put then get returns the cached result") {
        val cache = LatestVaRCache()
        val result = valuationResult(portfolioId = "port-1", varValue = 4200.0)

        cache.put("port-1", result)

        cache.get("port-1") shouldBe result
    }

    test("get returns null for unknown portfolio") {
        val cache = LatestVaRCache()

        cache.get("nonexistent").shouldBeNull()
    }

    test("put overwrites previous value") {
        val cache = LatestVaRCache()
        val first = valuationResult(portfolioId = "port-1", varValue = 1000.0)
        val second = valuationResult(portfolioId = "port-1", varValue = 9999.0)

        cache.put("port-1", first)
        cache.put("port-1", second)

        val cached = cache.get("port-1")
        cached shouldBe second
        cached!!.varValue shouldBe 9999.0
    }
})
