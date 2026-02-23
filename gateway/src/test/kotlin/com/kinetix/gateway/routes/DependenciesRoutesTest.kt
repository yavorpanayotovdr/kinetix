package com.kinetix.gateway.routes

import com.kinetix.gateway.client.*
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

private val sampleDependencies = DataDependenciesSummary(
    portfolioId = "port-1",
    dependencies = listOf(
        MarketDataDependencyItem(
            dataType = "SPOT_PRICE",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            required = true,
            description = "Current spot price for equity position valuation",
            parameters = emptyMap(),
        ),
        MarketDataDependencyItem(
            dataType = "HISTORICAL_PRICES",
            instrumentId = "AAPL",
            assetClass = "EQUITY",
            required = false,
            description = "Historical price series for volatility estimation",
            parameters = mapOf("lookbackDays" to "252"),
        ),
        MarketDataDependencyItem(
            dataType = "VOLATILITY_SURFACE",
            instrumentId = "AAPL-C-250-20260620",
            assetClass = "DERIVATIVE",
            required = true,
            description = "Implied volatility surface for option pricing",
            parameters = emptyMap(),
        ),
        MarketDataDependencyItem(
            dataType = "CORRELATION_MATRIX",
            instrumentId = "",
            assetClass = "",
            required = true,
            description = "Cross-asset correlation matrix for portfolio diversification",
            parameters = emptyMap(),
        ),
    ),
)

class DependenciesRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach {
        clearMocks(riskClient)
        // Provide default stubs for VaR interface methods
        coEvery { riskClient.calculateVaR(any()) } returns null
        coEvery { riskClient.getLatestVaR(any()) } returns null
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} returns 200 with dependencies") {
        coEvery { riskClient.discoverDependencies(any()) } returns sampleDependencies

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}""")
            }
            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"

            val deps = body["dependencies"]?.jsonArray
            deps?.size shouldBe 4

            // Check first dependency (SPOT_PRICE)
            val spot = deps!![0].jsonObject
            spot["dataType"]?.jsonPrimitive?.content shouldBe "SPOT_PRICE"
            spot["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            spot["assetClass"]?.jsonPrimitive?.content shouldBe "EQUITY"
            spot["required"]?.jsonPrimitive?.boolean shouldBe true
            spot["description"]?.jsonPrimitive?.content shouldBe "Current spot price for equity position valuation"
            spot["parameters"]?.jsonObject?.size shouldBe 0

            // Check second dependency (HISTORICAL_PRICES) with parameters
            val hist = deps[1].jsonObject
            hist["dataType"]?.jsonPrimitive?.content shouldBe "HISTORICAL_PRICES"
            hist["required"]?.jsonPrimitive?.boolean shouldBe false
            hist["parameters"]?.jsonObject?.get("lookbackDays")?.jsonPrimitive?.content shouldBe "252"

            // Check correlation matrix (portfolio-level)
            val corr = deps[3].jsonObject
            corr["dataType"]?.jsonPrimitive?.content shouldBe "CORRELATION_MATRIX"
            corr["instrumentId"]?.jsonPrimitive?.content shouldBe ""
            corr["assetClass"]?.jsonPrimitive?.content shouldBe ""
            corr["required"]?.jsonPrimitive?.boolean shouldBe true
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} with default parameters") {
        coEvery { riskClient.discoverDependencies(any()) } returns DataDependenciesSummary(
            portfolioId = "port-1",
            dependencies = emptyList(),
        )

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{}""")
            }
            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["portfolioId"]?.jsonPrimitive?.content shouldBe "port-1"
            body["dependencies"]?.jsonArray?.size shouldBe 0

            coVerify {
                riskClient.discoverDependencies(
                    match {
                        it.calculationType == "PARAMETRIC" && it.confidenceLevel == "CL_95"
                    }
                )
            }
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} returns 404 when client returns null") {
        coEvery { riskClient.discoverDependencies(any()) } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"PARAMETRIC","confidenceLevel":"CL_95"}""")
            }
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} passes calculation type to client") {
        coEvery { riskClient.discoverDependencies(any()) } returns DataDependenciesSummary(
            portfolioId = "port-1",
            dependencies = emptyList(),
        )

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"MONTE_CARLO","confidenceLevel":"CL_99"}""")
            }
            response.status shouldBe HttpStatusCode.OK

            coVerify {
                riskClient.discoverDependencies(
                    match {
                        it.portfolioId == "port-1" &&
                            it.calculationType == "MONTE_CARLO" &&
                            it.confidenceLevel == "CL_99"
                    }
                )
            }
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} returns 400 for invalid calculation type") {
        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"calculationType":"INVALID_TYPE"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/risk/dependencies/{portfolioId} returns 400 for invalid confidence level") {
        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/dependencies/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"confidenceLevel":"CL_50"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
