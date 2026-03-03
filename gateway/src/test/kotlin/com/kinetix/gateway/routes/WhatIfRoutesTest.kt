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
import java.time.Instant

private val sampleWhatIfResult = WhatIfResultSummary(
    baseVaR = "5000.00",
    baseExpectedShortfall = "6250.00",
    baseGreeks = null,
    basePositionRisk = emptyList(),
    hypotheticalVaR = "7500.00",
    hypotheticalExpectedShortfall = "9375.00",
    hypotheticalGreeks = null,
    hypotheticalPositionRisk = emptyList(),
    varChange = "2500.00",
    esChange = "3125.00",
    calculatedAt = "2026-03-01T10:00:00Z",
)

class WhatIfRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach {
        clearMocks(riskClient)
        coEvery { riskClient.calculateVaR(any()) } returns null
        coEvery { riskClient.getLatestVaR(any()) } returns null
    }

    test("POST /api/v1/risk/what-if/{portfolioId} proxies 200 with result") {
        coEvery { riskClient.runWhatIf(any()) } returns sampleWhatIfResult

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/what-if/port-1") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "hypotheticalTrades": [
                            {
                                "instrumentId": "SPY",
                                "assetClass": "EQUITY",
                                "side": "BUY",
                                "quantity": "100",
                                "priceAmount": "450.00",
                                "priceCurrency": "USD"
                            }
                        ]
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["baseVaR"]?.jsonPrimitive?.content shouldBe "5000.00"
            body["hypotheticalVaR"]?.jsonPrimitive?.content shouldBe "7500.00"
            body["varChange"]?.jsonPrimitive?.content shouldBe "2500.00"
            body["esChange"]?.jsonPrimitive?.content shouldBe "3125.00"
        }
    }

    test("POST /api/v1/risk/what-if/{portfolioId} returns 404 when service returns null") {
        coEvery { riskClient.runWhatIf(any()) } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.post("/api/v1/risk/what-if/port-1") {
                contentType(ContentType.Application.Json)
                setBody("""{"hypotheticalTrades": []}""")
            }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /api/v1/risk/what-if/{portfolioId} passes request params correctly") {
        coEvery { riskClient.runWhatIf(any()) } returns sampleWhatIfResult

        testApplication {
            application { module(riskClient) }
            client.post("/api/v1/risk/what-if/my-portfolio") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "hypotheticalTrades": [
                            {
                                "instrumentId": "AAPL",
                                "assetClass": "DERIVATIVE",
                                "side": "SELL",
                                "quantity": "200",
                                "priceAmount": "175.00",
                                "priceCurrency": "EUR"
                            }
                        ],
                        "calculationType": "MONTE_CARLO",
                        "confidenceLevel": "CL_99"
                    }
                    """.trimIndent()
                )
            }

            val slot = slot<WhatIfRequestParams>()
            coVerify { riskClient.runWhatIf(capture(slot)) }

            slot.captured.portfolioId shouldBe "my-portfolio"
            slot.captured.hypotheticalTrades.size shouldBe 1
            slot.captured.hypotheticalTrades[0].instrumentId shouldBe "AAPL"
            slot.captured.hypotheticalTrades[0].assetClass shouldBe "DERIVATIVE"
            slot.captured.hypotheticalTrades[0].side shouldBe "SELL"
            slot.captured.hypotheticalTrades[0].quantity shouldBe "200"
            slot.captured.hypotheticalTrades[0].priceAmount shouldBe "175.00"
            slot.captured.hypotheticalTrades[0].priceCurrency shouldBe "EUR"
            slot.captured.calculationType shouldBe "MONTE_CARLO"
            slot.captured.confidenceLevel shouldBe "CL_99"
        }
    }
})
