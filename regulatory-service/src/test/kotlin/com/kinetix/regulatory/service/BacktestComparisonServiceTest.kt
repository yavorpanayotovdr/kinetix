package com.kinetix.regulatory.service

import com.kinetix.regulatory.model.BacktestResultRecord
import com.kinetix.regulatory.persistence.BacktestResultRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

private fun backtestResult(
    id: String = UUID.randomUUID().toString(),
    bookId: String = "port-1",
    calculationType: String = "PARAMETRIC",
    confidenceLevel: Double = 0.99,
    totalDays: Int = 250,
    violationCount: Int = 3,
    violationRate: Double = 0.012,
    kupiecStatistic: Double = 0.5,
    kupiecPValue: Double = 0.48,
    kupiecPass: Boolean = true,
    christoffersenStatistic: Double = 0.3,
    christoffersenPValue: Double = 0.58,
    christoffersenPass: Boolean = true,
    trafficLightZone: String = "GREEN",
) = BacktestResultRecord(
    id = id,
    bookId = bookId,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
    totalDays = totalDays,
    violationCount = violationCount,
    violationRate = violationRate,
    kupiecStatistic = kupiecStatistic,
    kupiecPValue = kupiecPValue,
    kupiecPass = kupiecPass,
    christoffersenStatistic = christoffersenStatistic,
    christoffersenPValue = christoffersenPValue,
    christoffersenPass = christoffersenPass,
    trafficLightZone = trafficLightZone,
    calculatedAt = Instant.now(),
)

class BacktestComparisonServiceTest : FunSpec({

    val repository = mockk<BacktestResultRepository>()
    val service = BacktestComparisonService(repository)

    beforeEach { clearMocks(repository) }

    test("computes violation count diff between two backtest results") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()
        val base = backtestResult(id = baseId, violationCount = 3, violationRate = 0.012)
        val target = backtestResult(id = targetId, violationCount = 7, violationRate = 0.028)

        coEvery { repository.findById(baseId) } returns base
        coEvery { repository.findById(targetId) } returns target

        val result = service.compare(baseId, targetId)

        result.baseViolationCount shouldBe 3
        result.targetViolationCount shouldBe 7
        result.violationCountDiff shouldBe 4
    }

    test("detects traffic light zone change") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()
        val base = backtestResult(id = baseId, trafficLightZone = "GREEN")
        val target = backtestResult(id = targetId, trafficLightZone = "YELLOW")

        coEvery { repository.findById(baseId) } returns base
        coEvery { repository.findById(targetId) } returns target

        val result = service.compare(baseId, targetId)

        result.baseTrafficLightZone shouldBe "GREEN"
        result.targetTrafficLightZone shouldBe "YELLOW"
        result.trafficLightChanged shouldBe true
    }

    test("computes Kupiec and Christoffersen statistical diffs") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()
        val base = backtestResult(id = baseId, kupiecPValue = 0.48, christoffersenPValue = 0.58)
        val target = backtestResult(id = targetId, kupiecPValue = 0.02, christoffersenPValue = 0.15)

        coEvery { repository.findById(baseId) } returns base
        coEvery { repository.findById(targetId) } returns target

        val result = service.compare(baseId, targetId)

        result.baseKupiecPValue shouldBeExactly 0.48
        result.targetKupiecPValue shouldBeExactly 0.02
        result.baseChristoffersenPValue shouldBeExactly 0.58
        result.targetChristoffersenPValue shouldBeExactly 0.15
    }

    test("no traffic light change when zones are the same") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()
        val base = backtestResult(id = baseId, trafficLightZone = "GREEN")
        val target = backtestResult(id = targetId, trafficLightZone = "GREEN")

        coEvery { repository.findById(baseId) } returns base
        coEvery { repository.findById(targetId) } returns target

        val result = service.compare(baseId, targetId)

        result.trafficLightChanged shouldBe false
    }

    test("throws when base result not found") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()

        coEvery { repository.findById(baseId) } returns null
        coEvery { repository.findById(targetId) } returns backtestResult(id = targetId)

        shouldThrow<IllegalArgumentException> {
            service.compare(baseId, targetId)
        }
    }

    test("throws when target result not found") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()

        coEvery { repository.findById(baseId) } returns backtestResult(id = baseId)
        coEvery { repository.findById(targetId) } returns null

        shouldThrow<IllegalArgumentException> {
            service.compare(baseId, targetId)
        }
    }

    test("computes violation rate diff") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()
        val base = backtestResult(id = baseId, violationRate = 0.012)
        val target = backtestResult(id = targetId, violationRate = 0.028)

        coEvery { repository.findById(baseId) } returns base
        coEvery { repository.findById(targetId) } returns target

        val result = service.compare(baseId, targetId)

        result.baseViolationRate shouldBeExactly 0.012
        result.targetViolationRate shouldBeExactly 0.028
        result.violationRateDiff shouldBeExactly 0.028 - 0.012
    }

    test("populates base and target config from record fields") {
        val baseId = UUID.randomUUID().toString()
        val targetId = UUID.randomUUID().toString()
        val base = backtestResult(id = baseId, calculationType = "PARAMETRIC", confidenceLevel = 0.99, totalDays = 250)
        val target = backtestResult(id = targetId, calculationType = "HISTORICAL", confidenceLevel = 0.95, totalDays = 500)

        coEvery { repository.findById(baseId) } returns base
        coEvery { repository.findById(targetId) } returns target

        val result = service.compare(baseId, targetId)

        result.baseConfig.calculationType shouldBe "PARAMETRIC"
        result.baseConfig.confidenceLevel shouldBeExactly 0.99
        result.baseConfig.totalDays shouldBe 250
        result.targetConfig.calculationType shouldBe "HISTORICAL"
        result.targetConfig.confidenceLevel shouldBeExactly 0.95
        result.targetConfig.totalDays shouldBe 500
    }
})
