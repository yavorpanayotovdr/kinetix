package com.kinetix.gateway.routes

import com.kinetix.gateway.client.PositionRiskSummaryItem
import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.module
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.serialization.json.*

private val samplePositionRisk = listOf(
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
    PositionRiskSummaryItem(
        instrumentId = "MSFT",
        assetClass = "EQUITY",
        marketValue = "80000.00",
        delta = null,
        gamma = null,
        vega = null,
        varContribution = "1800.00",
        esContribution = "2300.00",
        percentageOfTotal = "36.00",
    ),
)

class PositionRiskRoutesTest : FunSpec({

    val riskClient = mockk<RiskServiceClient>()

    beforeEach { clearMocks(riskClient) }

    test("GET /api/v1/risk/positions/{portfolioId} returns 200 with position risk array") {
        coEvery { riskClient.getPositionRisk("port-1") } returns samplePositionRisk

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/positions/port-1")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2

            val first = body[0].jsonObject
            first["instrumentId"]?.jsonPrimitive?.content shouldBe "AAPL"
            first["assetClass"]?.jsonPrimitive?.content shouldBe "EQUITY"
            first["marketValue"]?.jsonPrimitive?.content shouldBe "150000.00"
            first["delta"]?.jsonPrimitive?.content shouldBe "0.850000"
            first["gamma"]?.jsonPrimitive?.content shouldBe "0.012000"
            first["vega"]?.jsonPrimitive?.content shouldBe "45.300000"
            first["varContribution"]?.jsonPrimitive?.content shouldBe "3200.00"
            first["esContribution"]?.jsonPrimitive?.content shouldBe "4100.00"
            first["percentageOfTotal"]?.jsonPrimitive?.content shouldBe "64.00"

            val second = body[1].jsonObject
            second["instrumentId"]?.jsonPrimitive?.content shouldBe "MSFT"
            second.containsKey("delta") shouldBe false
            second.containsKey("gamma") shouldBe false
            second.containsKey("vega") shouldBe false
        }
    }

    test("GET /api/v1/risk/positions/{portfolioId} returns 404 when no data") {
        coEvery { riskClient.getPositionRisk("unknown") } returns null

        testApplication {
            application { module(riskClient) }
            val response = client.get("/api/v1/risk/positions/unknown")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
