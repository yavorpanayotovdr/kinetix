package com.kinetix.risk.service

import com.kinetix.common.model.*
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.kafka.RiskResultPublisher
import com.kinetix.risk.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

private val USD = Currency.getInstance("USD")

private fun position(
    portfolioId: String = "port-1",
    instrumentId: String = "AAPL",
    assetClass: AssetClass = AssetClass.EQUITY,
    quantity: String = "100",
    marketPrice: String = "170.00",
) = Position(
    portfolioId = PortfolioId(portfolioId),
    instrumentId = InstrumentId(instrumentId),
    assetClass = assetClass,
    quantity = BigDecimal(quantity),
    averageCost = Money(BigDecimal("150.00"), USD),
    marketPrice = Money(BigDecimal(marketPrice), USD),
)

private fun varResult(
    portfolioId: String = "port-1",
    calculationType: CalculationType = CalculationType.PARAMETRIC,
    varValue: Double = 5000.0,
    componentBreakdown: List<ComponentBreakdown> = listOf(
        ComponentBreakdown(AssetClass.EQUITY, 5000.0, 100.0),
    ),
) = VaRResult(
    portfolioId = PortfolioId(portfolioId),
    calculationType = calculationType,
    confidenceLevel = ConfidenceLevel.CL_95,
    varValue = varValue,
    expectedShortfall = varValue * 1.25,
    componentBreakdown = componentBreakdown,
    calculatedAt = Instant.now(),
)

class VaRCalculationServiceTest : FunSpec({

    val positionProvider = mockk<PositionProvider>()
    val riskEngineClient = mockk<RiskEngineClient>()
    val resultPublisher = mockk<RiskResultPublisher>()
    val runRecorder = mockk<CalculationRunRecorder>()
    val service = VaRCalculationService(
        positionProvider, riskEngineClient, resultPublisher, SimpleMeterRegistry(),
        runRecorder = runRecorder,
    )
    val serviceNoRecorder = VaRCalculationService(positionProvider, riskEngineClient, resultPublisher, SimpleMeterRegistry())

    beforeEach {
        clearMocks(positionProvider, riskEngineClient, resultPublisher, runRecorder)
        coEvery { runRecorder.save(any()) } just Runs
    }

    test("fetches positions, calls risk engine, and publishes result") {
        val positions = listOf(position())
        val expectedResult = varResult()

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs

        val result = serviceNoRecorder.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result shouldBe expectedResult

        coVerify(ordering = Ordering.ORDERED) {
            positionProvider.getPositions(PortfolioId("port-1"))
            riskEngineClient.calculateVaR(any(), positions)
            resultPublisher.publish(expectedResult)
        }
    }

    test("returns null and does not call risk engine for empty portfolio") {
        coEvery { positionProvider.getPositions(PortfolioId("empty")) } returns emptyList()

        val result = serviceNoRecorder.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("empty"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result shouldBe null

        coVerify(exactly = 0) { riskEngineClient.calculateVaR(any(), any()) }
        coVerify(exactly = 0) { resultPublisher.publish(any()) }
    }

    test("passes correct calculation type to risk engine") {
        val positions = listOf(position())

        for (calcType in CalculationType.entries) {
            val expectedResult = varResult(calculationType = calcType)

            coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
            coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
            coEvery { resultPublisher.publish(any()) } just Runs

            val result = serviceNoRecorder.calculateVaR(
                VaRCalculationRequest(
                    portfolioId = PortfolioId("port-1"),
                    calculationType = calcType,
                    confidenceLevel = ConfidenceLevel.CL_95,
                )
            )

            result!!.calculationType shouldBe calcType
        }
    }

    test("handles multi-asset portfolio") {
        val positions = listOf(
            position(instrumentId = "AAPL", assetClass = AssetClass.EQUITY),
            position(instrumentId = "UST10Y", assetClass = AssetClass.FIXED_INCOME),
            position(instrumentId = "EURUSD", assetClass = AssetClass.FX),
        )
        val expectedResult = varResult(
            componentBreakdown = listOf(
                ComponentBreakdown(AssetClass.EQUITY, 3000.0, 50.0),
                ComponentBreakdown(AssetClass.FIXED_INCOME, 1500.0, 25.0),
                ComponentBreakdown(AssetClass.FX, 1500.0, 25.0),
            ),
        )

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(any()) } just Runs

        val result = serviceNoRecorder.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result!!.componentBreakdown.size shouldBe 3
    }

    test("records a completed calculation run with all pipeline steps") {
        val positions = listOf(position())
        val expectedResult = varResult()

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs

        service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        val runSlot = slot<CalculationRun>()
        coVerify { runRecorder.save(capture(runSlot)) }

        val run = runSlot.captured
        run.portfolioId shouldBe "port-1"
        run.status shouldBe RunStatus.COMPLETED
        run.triggerType shouldBe TriggerType.ON_DEMAND
        run.calculationType shouldBe "PARAMETRIC"
        run.confidenceLevel shouldBe "CL_95"
        run.varValue shouldBe expectedResult.varValue
        run.expectedShortfall shouldBe expectedResult.expectedShortfall
        run.completedAt.shouldNotBeNull()
        run.durationMs.shouldNotBeNull()
        run.error shouldBe null

        run.steps shouldHaveSize 5
        run.steps[0].name shouldBe PipelineStepName.FETCH_POSITIONS
        run.steps[1].name shouldBe PipelineStepName.DISCOVER_DEPENDENCIES
        run.steps[2].name shouldBe PipelineStepName.FETCH_MARKET_DATA
        run.steps[3].name shouldBe PipelineStepName.CALCULATE_VAR
        run.steps[4].name shouldBe PipelineStepName.PUBLISH_RESULT

        run.steps[0].details["positionCount"] shouldBe 1

        val positionsJson = run.steps[0].details["positions"]
        positionsJson.shouldBeInstanceOf<String>()
        positionsJson shouldContain "AAPL"
        positionsJson shouldContain "EQUITY"
        positionsJson shouldContain "100"
    }

    test("records a failed run when risk engine throws") {
        val positions = listOf(position())

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } throws RuntimeException("Engine down")

        try {
            service.calculateVaR(
                VaRCalculationRequest(
                    portfolioId = PortfolioId("port-1"),
                    calculationType = CalculationType.PARAMETRIC,
                    confidenceLevel = ConfidenceLevel.CL_95,
                )
            )
        } catch (_: RuntimeException) {
            // expected
        }

        val runSlot = slot<CalculationRun>()
        coVerify { runRecorder.save(capture(runSlot)) }

        val run = runSlot.captured
        run.status shouldBe RunStatus.FAILED
        run.error shouldBe "Engine down"
    }

    test("does not fail the calculation if run recorder throws") {
        val positions = listOf(position())
        val expectedResult = varResult()

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs
        coEvery { runRecorder.save(any()) } throws RuntimeException("DB connection failed")

        val result = service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result shouldBe expectedResult
    }

    test("passes trigger type through to the recorded run") {
        val positions = listOf(position())
        val expectedResult = varResult()

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs

        service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            ),
            triggerType = TriggerType.TRADE_EVENT,
        )

        val runSlot = slot<CalculationRun>()
        coVerify { runRecorder.save(capture(runSlot)) }

        runSlot.captured.triggerType shouldBe TriggerType.TRADE_EVENT
    }

    test("captures discovered dependencies in step details") {
        val positions = listOf(position())
        val expectedResult = varResult()
        val dependencies = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
            DiscoveredDependency("HISTORICAL_PRICES", "AAPL", "EQUITY", mapOf("lookback" to "252")),
        )

        val discoverer = mockk<DependenciesDiscoverer>()
        coEvery { discoverer.discover(any(), any(), any()) } returns dependencies
        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions, any()) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs

        val serviceWithDiscoverer = VaRCalculationService(
            positionProvider, riskEngineClient, resultPublisher, SimpleMeterRegistry(),
            dependenciesDiscoverer = discoverer,
            runRecorder = runRecorder,
        )

        serviceWithDiscoverer.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        val runSlot = slot<CalculationRun>()
        coVerify { runRecorder.save(capture(runSlot)) }

        val discoverStep = runSlot.captured.steps.first { it.name == PipelineStepName.DISCOVER_DEPENDENCIES }
        discoverStep.details["dependencyCount"] shouldBe 2

        val depsJson = discoverStep.details["dependencies"]
        depsJson.shouldBeInstanceOf<String>()

        val parsed = Json.parseToJsonElement(depsJson).jsonArray
        parsed shouldHaveSize 2

        parsed[0].jsonObject["instrumentId"]!!.jsonPrimitive.content shouldBe "AAPL"
        parsed[0].jsonObject["dataType"]!!.jsonPrimitive.content shouldBe "SPOT_PRICE"
        parsed[0].jsonObject["assetClass"]!!.jsonPrimitive.content shouldBe "EQUITY"

        parsed[1].jsonObject["instrumentId"]!!.jsonPrimitive.content shouldBe "AAPL"
        parsed[1].jsonObject["dataType"]!!.jsonPrimitive.content shouldBe "HISTORICAL_PRICES"
        parsed[1].jsonObject["parameters"]!!.jsonPrimitive.content shouldContain "lookback=252"
    }
})
