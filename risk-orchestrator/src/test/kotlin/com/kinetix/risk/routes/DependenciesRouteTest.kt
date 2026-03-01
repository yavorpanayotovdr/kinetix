package com.kinetix.risk.routes

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.routes.dtos.*
import com.kinetix.proto.risk.DataDependenciesResponse as ProtoDataDependenciesResponse
import com.kinetix.proto.risk.MarketDataDependency as ProtoMarketDataDependency
import com.kinetix.proto.risk.MarketDataType
import com.kinetix.risk.cache.InMemoryVaRCache
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.service.VaRCalculationService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class DependenciesRouteTest : FunSpec({

    val positionProvider = mockk<PositionProvider>()
    val riskEngineClient = mockk<RiskEngineClient>()
    val varCalculationService = mockk<VaRCalculationService>()
    val varCache = InMemoryVaRCache()
    val stressTestStub = mockk<com.kinetix.proto.risk.StressTestServiceGrpcKt.StressTestServiceCoroutineStub>(relaxed = true)
    val regulatoryStub = mockk<com.kinetix.proto.risk.RegulatoryReportingServiceGrpcKt.RegulatoryReportingServiceCoroutineStub>(relaxed = true)

    beforeEach {
        clearMocks(positionProvider, riskEngineClient, varCalculationService)
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} returns dependencies") {
        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns emptyList()
        coEvery { riskEngineClient.discoverDependencies(any(), any(), any()) } returns
            ProtoDataDependenciesResponse.newBuilder()
                .addDependencies(
                    ProtoMarketDataDependency.newBuilder()
                        .setDataType(MarketDataType.SPOT_PRICE)
                        .setInstrumentId("AAPL")
                        .setAssetClass("EQUITY")
                        .setRequired(true)
                        .setDescription("Current spot price for equity position valuation")
                        .build()
                )
                .addDependencies(
                    ProtoMarketDataDependency.newBuilder()
                        .setDataType(MarketDataType.HISTORICAL_PRICES)
                        .setInstrumentId("AAPL")
                        .setAssetClass("EQUITY")
                        .setRequired(false)
                        .setDescription("Historical price series for volatility estimation")
                        .putParameters("lookbackDays", "252")
                        .build()
                )
                .build()

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, varCache, positionProvider, stressTestStub, regulatoryStub, riskEngineClient)
            }

            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"

            val deps = body["dependencies"]?.jsonArray
            deps?.size shouldBe 2

            val first = deps!![0].jsonObject
            first["dataType"]?.jsonPrimitive?.content shouldBe "SPOT_PRICE"
            first["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            first["assetClass"]?.jsonPrimitive?.content shouldBe "EQUITY"
            first["required"]?.jsonPrimitive?.boolean shouldBe true
            first["description"]?.jsonPrimitive?.content shouldBe "Current spot price for equity position valuation"

            val second = deps[1].jsonObject
            second["dataType"]?.jsonPrimitive?.content shouldBe "HISTORICAL_PRICES"
            second["required"]?.jsonPrimitive?.boolean shouldBe false
            val params = second["parameters"]?.jsonObject
            params?.get("lookbackDays")?.jsonPrimitive?.content shouldBe "252"
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} uses default calc type and confidence level") {
        coEvery { positionProvider.getPositions(PortfolioId("port-1")) } returns emptyList()
        coEvery { riskEngineClient.discoverDependencies(any(), any(), any()) } returns
            ProtoDataDependenciesResponse.getDefaultInstance()

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, varCache, positionProvider, stressTestStub, regulatoryStub, riskEngineClient)
            }

            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["dependencies"]?.jsonArray?.size shouldBe 0

            coVerify {
                riskEngineClient.discoverDependencies(any(), "PARAMETRIC", "CL_95")
            }
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} with MONTE_CARLO") {
        coEvery { positionProvider.getPositions(PortfolioId("port-2")) } returns emptyList()
        coEvery { riskEngineClient.discoverDependencies(any(), any(), any()) } returns
            ProtoDataDependenciesResponse.newBuilder()
                .addDependencies(
                    ProtoMarketDataDependency.newBuilder()
                        .setDataType(MarketDataType.CORRELATION_MATRIX)
                        .setInstrumentId("")
                        .setAssetClass("")
                        .setRequired(true)
                        .setDescription("Cross-asset correlation matrix")
                        .build()
                )
                .build()

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, varCache, positionProvider, stressTestStub, regulatoryStub, riskEngineClient)
            }

            val response = client.post("/api/v1/risk/dependencies/port-2") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"MONTE_CARLO","confidenceLevel":"CL_99"}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["portfolioId"]?.jsonPrimitive?.content shouldBe "port-2"

            val deps = body["dependencies"]?.jsonArray
            deps?.size shouldBe 1
            deps!![0].jsonObject["dataType"]?.jsonPrimitive?.content shouldBe "CORRELATION_MATRIX"
            deps[0].jsonObject["instrumentId"]?.jsonPrimitive?.content shouldBe ""

            coVerify {
                riskEngineClient.discoverDependencies(any(), "MONTE_CARLO", "CL_99")
            }
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} returns empty list when no positions") {
        coEvery { positionProvider.getPositions(PortfolioId("empty-portfolio")) } returns emptyList()
        coEvery { riskEngineClient.discoverDependencies(any(), any(), any()) } returns
            ProtoDataDependenciesResponse.getDefaultInstance()

        testApplication {
            install(ContentNegotiation) { json() }
            routing {
                riskRoutes(varCalculationService, varCache, positionProvider, stressTestStub, regulatoryStub, riskEngineClient)
            }

            val response = client.post("/api/v1/risk/dependencies/empty-portfolio") {
                contentType(ContentType.Application.Json)
                setBody("""{}""")
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["dependencies"]?.jsonArray?.size shouldBe 0
        }
    }
})
