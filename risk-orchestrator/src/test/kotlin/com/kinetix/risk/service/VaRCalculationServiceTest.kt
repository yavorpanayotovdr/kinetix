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
    val jobRecorder = mockk<ValuationJobRecorder>()
    val service = VaRCalculationService(
        positionProvider, riskEngineClient, resultPublisher, SimpleMeterRegistry(),
        jobRecorder = jobRecorder,
    )
    val serviceNoRecorder = VaRCalculationService(positionProvider, riskEngineClient, resultPublisher, SimpleMeterRegistry())

    beforeEach {
        clearMocks(positionProvider, riskEngineClient, resultPublisher, jobRecorder)
        coEvery { jobRecorder.save(any()) } just Runs
        coEvery { jobRecorder.update(any()) } just Runs
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

    test("saves a STARTED skeleton job at the beginning of calculation") {
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

        val saveSlot = slot<ValuationJob>()
        coVerify { jobRecorder.save(capture(saveSlot)) }

        val startedJob = saveSlot.captured
        startedJob.portfolioId shouldBe "port-1"
        startedJob.status shouldBe RunStatus.STARTED
        startedJob.triggerType shouldBe TriggerType.ON_DEMAND
        startedJob.calculationType shouldBe "PARAMETRIC"
        startedJob.confidenceLevel shouldBe "CL_95"
        startedJob.completedAt shouldBe null
        startedJob.durationMs shouldBe null
        startedJob.varValue shouldBe null
        startedJob.expectedShortfall shouldBe null
        startedJob.steps shouldHaveSize 0
        startedJob.error shouldBe null
    }

    test("updates the job to COMPLETED with all job steps after calculation") {
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

        val updateSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(updateSlot)) }

        val job = updateSlot.captured
        job.portfolioId shouldBe "port-1"
        job.status shouldBe RunStatus.COMPLETED
        job.triggerType shouldBe TriggerType.ON_DEMAND
        job.calculationType shouldBe "PARAMETRIC"
        job.confidenceLevel shouldBe "CL_95"
        job.varValue shouldBe expectedResult.varValue
        job.expectedShortfall shouldBe expectedResult.expectedShortfall
        job.completedAt.shouldNotBeNull()
        job.durationMs.shouldNotBeNull()
        job.error shouldBe null

        job.steps shouldHaveSize 5
        job.steps[0].name shouldBe JobStepName.FETCH_POSITIONS
        job.steps[1].name shouldBe JobStepName.DISCOVER_DEPENDENCIES
        job.steps[2].name shouldBe JobStepName.FETCH_MARKET_DATA
        job.steps[3].name shouldBe JobStepName.CALCULATE_VAR
        job.steps[4].name shouldBe JobStepName.PUBLISH_RESULT

        job.steps[0].details["positionCount"] shouldBe 1

        val positionsJson = job.steps[0].details["positions"]
        positionsJson.shouldBeInstanceOf<String>()
        positionsJson shouldContain "AAPL"
        positionsJson shouldContain "EQUITY"
        positionsJson shouldContain "100"
    }

    test("updates the job to FAILED when risk engine throws") {
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

        val saveSlot = slot<ValuationJob>()
        coVerify { jobRecorder.save(capture(saveSlot)) }
        saveSlot.captured.status shouldBe RunStatus.STARTED

        val updateSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(updateSlot)) }
        updateSlot.captured.status shouldBe RunStatus.FAILED
        updateSlot.captured.error shouldBe "Engine down"
    }

    test("does not fail the calculation if job recorder save throws") {
        val positions = listOf(position())
        val expectedResult = varResult()

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs
        coEvery { jobRecorder.save(any()) } throws RuntimeException("DB connection failed")

        val result = service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result shouldBe expectedResult
    }

    test("does not fail the calculation if job recorder update throws") {
        val positions = listOf(position())
        val expectedResult = varResult()

        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs
        coEvery { jobRecorder.update(any()) } throws RuntimeException("DB connection failed")

        val result = service.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        result shouldBe expectedResult
    }

    test("passes trigger type through to the recorded job") {
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

        val saveSlot = slot<ValuationJob>()
        coVerify { jobRecorder.save(capture(saveSlot)) }
        saveSlot.captured.triggerType shouldBe TriggerType.TRADE_EVENT

        val updateSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(updateSlot)) }
        updateSlot.captured.triggerType shouldBe TriggerType.TRADE_EVENT
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
            jobRecorder = jobRecorder,
        )

        serviceWithDiscoverer.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        val jobSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(jobSlot)) }

        val discoverStep = jobSlot.captured.steps.first { it.name == JobStepName.DISCOVER_DEPENDENCIES }
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

    test("captures market data items with fetch status in step details") {
        val positions = listOf(position())
        val expectedResult = varResult()
        val dependencies = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
            DiscoveredDependency("YIELD_CURVE", "USD_SOFR", "RATES"),
        )
        val marketData = listOf<MarketDataValue>(
            ScalarMarketData("SPOT_PRICE", "AAPL", "EQUITY", 170.5),
        )

        val discoverer = mockk<DependenciesDiscoverer>()
        val fetcher = mockk<MarketDataFetcher>()
        coEvery { discoverer.discover(any(), any(), any()) } returns dependencies
        coEvery { fetcher.fetch(dependencies) } returns marketData
        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions, marketData) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs

        val serviceWithFetcher = VaRCalculationService(
            positionProvider, riskEngineClient, resultPublisher, SimpleMeterRegistry(),
            dependenciesDiscoverer = discoverer,
            marketDataFetcher = fetcher,
            jobRecorder = jobRecorder,
        )

        serviceWithFetcher.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        val jobSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(jobSlot)) }

        val mdStep = jobSlot.captured.steps.first { it.name == JobStepName.FETCH_MARKET_DATA }
        mdStep.details["requested"] shouldBe 2
        mdStep.details["fetched"] shouldBe 1

        val itemsJson = mdStep.details["marketDataItems"]
        itemsJson.shouldBeInstanceOf<String>()

        val parsed = Json.parseToJsonElement(itemsJson).jsonArray
        parsed shouldHaveSize 2

        val spotItem = parsed[0].jsonObject
        spotItem["instrumentId"]!!.jsonPrimitive.content shouldBe "AAPL"
        spotItem["dataType"]!!.jsonPrimitive.content shouldBe "SPOT_PRICE"
        spotItem["status"]!!.jsonPrimitive.content shouldBe "FETCHED"
        spotItem["value"]!!.jsonPrimitive.content shouldBe "170.5"

        val curveItem = parsed[1].jsonObject
        curveItem["instrumentId"]!!.jsonPrimitive.content shouldBe "USD_SOFR"
        curveItem["dataType"]!!.jsonPrimitive.content shouldBe "YIELD_CURVE"
        curveItem["status"]!!.jsonPrimitive.content shouldBe "MISSING"
    }

    test("FETCH_POSITIONS step contains dependenciesByPosition when dependencies are discovered") {
        val positions = listOf(
            position(instrumentId = "AAPL", assetClass = AssetClass.EQUITY),
            position(instrumentId = "UST10Y", assetClass = AssetClass.FIXED_INCOME),
        )
        val expectedResult = varResult()
        val dependencies = listOf(
            DiscoveredDependency("SPOT_PRICE", "AAPL", "EQUITY"),
            DiscoveredDependency("YIELD_CURVE", "", "FIXED_INCOME"),
            DiscoveredDependency("CORRELATION_MATRIX", "", ""),
        )

        val discoverer = mockk<DependenciesDiscoverer>()
        coEvery { discoverer.discover(any(), any(), any()) } returns dependencies
        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns positions
        coEvery { riskEngineClient.calculateVaR(any(), positions, any()) } returns expectedResult
        coEvery { resultPublisher.publish(expectedResult) } just Runs

        val serviceWithDiscoverer = VaRCalculationService(
            positionProvider, riskEngineClient, resultPublisher, SimpleMeterRegistry(),
            dependenciesDiscoverer = discoverer,
            jobRecorder = jobRecorder,
        )

        serviceWithDiscoverer.calculateVaR(
            VaRCalculationRequest(
                portfolioId = PortfolioId("port-1"),
                calculationType = CalculationType.PARAMETRIC,
                confidenceLevel = ConfidenceLevel.CL_95,
            )
        )

        val jobSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(jobSlot)) }

        val fetchPosStep = jobSlot.captured.steps.first { it.name == JobStepName.FETCH_POSITIONS }
        val groupedJson = fetchPosStep.details["dependenciesByPosition"]
        groupedJson.shouldBeInstanceOf<String>()

        val parsed = Json.parseToJsonElement(groupedJson).jsonObject
        parsed.keys shouldBe setOf("AAPL", "UST10Y")

        val aaplDeps = parsed["AAPL"]!!.jsonArray
        aaplDeps shouldHaveSize 2
        aaplDeps.map { it.jsonObject["dataType"]!!.jsonPrimitive.content }.toSet() shouldBe setOf("SPOT_PRICE", "CORRELATION_MATRIX")

        val ustDeps = parsed["UST10Y"]!!.jsonArray
        ustDeps shouldHaveSize 2
        ustDeps.map { it.jsonObject["dataType"]!!.jsonPrimitive.content }.toSet() shouldBe setOf("YIELD_CURVE", "CORRELATION_MATRIX")
    }

    test("includes per-position VaR breakdown in CALCULATE_VAR step details") {
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

        val jobSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(jobSlot)) }

        val calcStep = jobSlot.captured.steps.first { it.name == JobStepName.CALCULATE_VAR }
        val breakdownJson = calcStep.details["positionBreakdown"]
        breakdownJson.shouldBeInstanceOf<String>()

        val parsed = Json.parseToJsonElement(breakdownJson).jsonArray
        parsed shouldHaveSize 1

        val item = parsed[0].jsonObject
        item["instrumentId"]!!.jsonPrimitive.content shouldBe "AAPL"
        item["assetClass"]!!.jsonPrimitive.content shouldBe "EQUITY"
        item["varContribution"]!!.jsonPrimitive.content shouldBe "5000.00"
        item["esContribution"]!!.jsonPrimitive.content shouldBe "6250.00"
        item["percentageOfTotal"]!!.jsonPrimitive.content shouldBe "100.00"
    }

    test("distributes VaR proportionally across positions in the same asset class") {
        val positions = listOf(
            position(instrumentId = "AAPL", marketPrice = "200.00"),
            position(instrumentId = "TSLA", marketPrice = "100.00"),
        )
        val expectedResult = varResult(
            varValue = 6000.0,
            componentBreakdown = listOf(
                ComponentBreakdown(AssetClass.EQUITY, 6000.0, 100.0),
            ),
        )

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

        val jobSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(jobSlot)) }

        val calcStep = jobSlot.captured.steps.first { it.name == JobStepName.CALCULATE_VAR }
        val parsed = Json.parseToJsonElement(calcStep.details["positionBreakdown"] as String).jsonArray
        parsed shouldHaveSize 2

        val aapl = parsed.first { it.jsonObject["instrumentId"]!!.jsonPrimitive.content == "AAPL" }.jsonObject
        aapl["varContribution"]!!.jsonPrimitive.content shouldBe "4000.00"
        aapl["esContribution"]!!.jsonPrimitive.content shouldBe "5000.00"
        aapl["percentageOfTotal"]!!.jsonPrimitive.content shouldBe "66.67"

        val tsla = parsed.first { it.jsonObject["instrumentId"]!!.jsonPrimitive.content == "TSLA" }.jsonObject
        tsla["varContribution"]!!.jsonPrimitive.content shouldBe "2000.00"
        tsla["esContribution"]!!.jsonPrimitive.content shouldBe "2500.00"
        tsla["percentageOfTotal"]!!.jsonPrimitive.content shouldBe "33.33"
    }

    test("dependenciesByPosition absent when no dependencies are discovered") {
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

        val jobSlot = slot<ValuationJob>()
        coVerify { jobRecorder.update(capture(jobSlot)) }

        val fetchPosStep = jobSlot.captured.steps.first { it.name == JobStepName.FETCH_POSITIONS }
        fetchPosStep.details.containsKey("dependenciesByPosition") shouldBe false
    }
})
