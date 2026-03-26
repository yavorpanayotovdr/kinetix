package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.CorrelationServiceClient
import com.kinetix.regulatory.client.CorrelationMatrix
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double

class CreateCorrelatedScenarioServiceTest : FunSpec({

    val repository = mockk<StressScenarioRepository>()
    val correlationClient = mockk<CorrelationServiceClient>()
    val service = StressScenarioService(
        repository = repository,
        correlationServiceClient = correlationClient,
    )

    beforeTest {
        clearMocks(repository, correlationClient)
        coEvery { repository.save(any()) } returns Unit
    }

    // Correlation matrix for 3 asset classes: EQUITY, RATES, CREDIT
    // Encoded as flat row-major list matching labels order.
    //
    //          EQUITY  RATES  CREDIT
    // EQUITY     1.0   -0.3    0.5
    // RATES     -0.3    1.0   -0.2
    // CREDIT     0.5   -0.2    1.0
    //
    // For a primary shock on EQUITY of -0.20:
    //   RATES shock  = corr(EQUITY,RATES)  * primary = -0.3 * (-0.20) =  0.06
    //   CREDIT shock = corr(EQUITY,CREDIT) * primary =  0.5 * (-0.20) = -0.10
    val aCorrelationMatrix = CorrelationMatrix(
        labels = listOf("EQUITY", "RATES", "CREDIT"),
        values = listOf(
            1.0, -0.3, 0.5,
            -0.3, 1.0, -0.2,
            0.5, -0.2, 1.0,
        ),
    )

    test("creates a parametric scenario in DRAFT status using correlated shocks") {
        coEvery { correlationClient.fetchLatestMatrix(any()) } returns aCorrelationMatrix

        val result = service.createCorrelatedScenario(
            name = "Equity crash with correlations",
            description = "GFC-style equity shock with derived secondary shocks",
            primaryAssetClass = "EQUITY",
            primaryShock = -0.20,
            assetClasses = listOf("EQUITY", "RATES", "CREDIT"),
            createdBy = "risk-analyst-1",
        )

        result.name shouldBe "Equity crash with correlations"
        result.status shouldBe ScenarioStatus.DRAFT
        result.scenarioType shouldBe ScenarioType.PARAMETRIC
        result.createdBy shouldBe "risk-analyst-1"
        result.shocks shouldNotBe null
    }

    test("derives secondary shocks from the correlation matrix and primary shock magnitude") {
        coEvery { correlationClient.fetchLatestMatrix(any()) } returns aCorrelationMatrix

        val result = service.createCorrelatedScenario(
            name = "Equity crash",
            description = "Test",
            primaryAssetClass = "EQUITY",
            primaryShock = -0.20,
            assetClasses = listOf("EQUITY", "RATES", "CREDIT"),
            createdBy = "analyst-1",
        )

        val shocks = Json.parseToJsonElement(result.shocks).jsonObject
        val equityShock = shocks["EQUITY"]!!.jsonPrimitive.double
        val ratesShock = shocks["RATES"]!!.jsonPrimitive.double
        val creditShock = shocks["CREDIT"]!!.jsonPrimitive.double

        // Primary shock is preserved as-is
        equityShock shouldBeCloseTo -0.20 delta 0.0001
        // RATES: -0.3 * (-0.20) = 0.06
        ratesShock shouldBeCloseTo 0.06 delta 0.0001
        // CREDIT: 0.5 * (-0.20) = -0.10
        creditShock shouldBeCloseTo -0.10 delta 0.0001
    }

    test("fetches the correlation matrix for the specified asset classes") {
        coEvery { correlationClient.fetchLatestMatrix(listOf("EQUITY", "RATES", "CREDIT")) } returns aCorrelationMatrix

        service.createCorrelatedScenario(
            name = "Test",
            description = "Test",
            primaryAssetClass = "EQUITY",
            primaryShock = -0.20,
            assetClasses = listOf("EQUITY", "RATES", "CREDIT"),
            createdBy = "analyst-1",
        )

        coVerify(exactly = 1) { correlationClient.fetchLatestMatrix(listOf("EQUITY", "RATES", "CREDIT")) }
    }

    test("saves the scenario to the repository") {
        coEvery { correlationClient.fetchLatestMatrix(any()) } returns aCorrelationMatrix

        service.createCorrelatedScenario(
            name = "Test",
            description = "Test",
            primaryAssetClass = "EQUITY",
            primaryShock = -0.20,
            assetClasses = listOf("EQUITY", "RATES", "CREDIT"),
            createdBy = "analyst-1",
        )

        coVerify(exactly = 1) { repository.save(any()) }
    }

    test("rejects request when primary asset class is not in the asset class list") {
        shouldThrow<IllegalArgumentException> {
            service.createCorrelatedScenario(
                name = "Test",
                description = "Test",
                primaryAssetClass = "FX",
                primaryShock = -0.15,
                assetClasses = listOf("EQUITY", "RATES", "CREDIT"),
                createdBy = "analyst-1",
            )
        }
    }

    test("rejects when correlation matrix is unavailable") {
        coEvery { correlationClient.fetchLatestMatrix(any()) } returns null

        shouldThrow<IllegalStateException> {
            service.createCorrelatedScenario(
                name = "Test",
                description = "Test",
                primaryAssetClass = "EQUITY",
                primaryShock = -0.20,
                assetClasses = listOf("EQUITY", "RATES", "CREDIT"),
                createdBy = "analyst-1",
            )
        }
    }

    test("serialises all asset class shocks in the stored shocks JSON") {
        coEvery { correlationClient.fetchLatestMatrix(any()) } returns aCorrelationMatrix

        val result = service.createCorrelatedScenario(
            name = "Multi-factor shock",
            description = "Test",
            primaryAssetClass = "EQUITY",
            primaryShock = -0.30,
            assetClasses = listOf("EQUITY", "RATES", "CREDIT"),
            createdBy = "analyst-1",
        )

        val shocks = Json.parseToJsonElement(result.shocks).jsonObject
        shocks.containsKey("EQUITY") shouldBe true
        shocks.containsKey("RATES") shouldBe true
        shocks.containsKey("CREDIT") shouldBe true
    }
})

private infix fun Double.shouldBeCloseTo(expected: Double) = CorrelatedShockDeltaMatcher(this, expected)

private class CorrelatedShockDeltaMatcher(private val actual: Double, private val expected: Double) {
    infix fun delta(delta: Double) {
        val diff = Math.abs(actual - expected)
        if (diff > delta) {
            throw AssertionError("Expected $actual to be within $delta of $expected, but difference was $diff")
        }
    }
}
