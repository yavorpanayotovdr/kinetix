package com.kinetix.gateway.contract

import com.kinetix.gateway.client.PositionRiskSummaryItem
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

class GatewayPositionRiskContractAcceptanceTest : BehaviorSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    given("gateway routing to risk-orchestrator") {

        `when`("GET /api/v1/risk/positions/{portfolioId} with position risk data") {
            then("returns 200 with position risk array containing expected fields") {
                coEvery { riskClient.getPositionRisk("equity-growth") } returns listOf(
                    PositionRiskSummaryItem(
                        instrumentId = "AAPL",
                        assetClass = "EQUITY",
                        marketValue = "150000.00",
                        delta = "0.850000",
                        gamma = "0.012000",
                        vega = "45.300000",
                        varContribution = "3200.00",
                        esContribution = "4100.00",
                        percentageOfTotal = "64.00",
                    ),
                )

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/positions/equity-growth")

                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 1

                    val item = body[0].jsonObject
                    item.containsKey("instrumentId") shouldBe true
                    item.containsKey("assetClass") shouldBe true
                    item.containsKey("marketValue") shouldBe true
                    item.containsKey("delta") shouldBe true
                    item.containsKey("gamma") shouldBe true
                    item.containsKey("vega") shouldBe true
                    item.containsKey("varContribution") shouldBe true
                    item.containsKey("esContribution") shouldBe true
                    item.containsKey("percentageOfTotal") shouldBe true

                    item["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
                    item["assetClass"]?.jsonPrimitive?.content shouldBe "EQUITY"
                    item["marketValue"]?.jsonPrimitive?.content shouldBe "150000.00"
                    item["varContribution"]?.jsonPrimitive?.content shouldBe "3200.00"
                    item["esContribution"]?.jsonPrimitive?.content shouldBe "4100.00"
                    item["percentageOfTotal"]?.jsonPrimitive?.content shouldBe "64.00"
                }
            }
        }

        `when`("GET /api/v1/risk/positions/{portfolioId} with no position risk data") {
            then("returns 404") {
                coEvery { riskClient.getPositionRisk("empty-portfolio") } returns null

                testApplication {
                    application { module(riskClient) }
                    val response = client.get("/api/v1/risk/positions/empty-portfolio")

                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }
})
