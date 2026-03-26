package com.kinetix.risk.routes

import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.HierarchyNodeRisk
import com.kinetix.risk.model.RiskContributor
import com.kinetix.risk.service.HierarchyRiskService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray

private val json = Json { ignoreUnknownKeys = true }

private fun firmNodeWithUtilisation(utilisationPct: Double? = 40.0) = HierarchyNodeRisk(
    level = HierarchyLevel.FIRM,
    entityId = "FIRM",
    entityName = "FIRM",
    parentId = null,
    varValue = 2_000_000.0,
    expectedShortfall = 2_500_000.0,
    pnlToday = null,
    limitUtilisation = utilisationPct,
    marginalVar = null,
    incrementalVar = null,
    topContributors = listOf(
        RiskContributor("div-equities", "Equities", 1_200_000.0, 60.0),
        RiskContributor("div-rates", "Rates", 800_000.0, 40.0),
    ),
    childCount = 2,
    isPartial = false,
    missingBooks = emptyList(),
)

class CroReportRoutesAcceptanceTest : FunSpec({

    val hierarchyRiskService = mockk<HierarchyRiskService>()

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(json) }
            routing { croReportRoutes(hierarchyRiskService) }
            block()
        }
    }

    test("POST /api/v1/risk/reports/cro returns 200 with firm-level report") {
        coEvery { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") } returns
            firmNodeWithUtilisation()

        testApp {
            val response = client.post("/api/v1/risk/reports/cro")
            response.status shouldBe HttpStatusCode.OK

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["level"]?.jsonPrimitive?.content shouldBe "FIRM"
            body["entityId"]?.jsonPrimitive?.content shouldBe "FIRM"
            body["varValue"]?.jsonPrimitive?.content shouldBe "2000000.00"
        }
    }

    test("report includes limitUtilisation when budget is configured") {
        coEvery { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") } returns
            firmNodeWithUtilisation(utilisationPct = 85.0)

        testApp {
            val response = client.post("/api/v1/risk/reports/cro")
            response.status shouldBe HttpStatusCode.OK

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["limitUtilisation"]?.jsonPrimitive?.content shouldBe "85.00"
        }
    }

    test("report includes topContributors array") {
        coEvery { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") } returns
            firmNodeWithUtilisation()

        testApp {
            val response = client.post("/api/v1/risk/reports/cro")
            response.status shouldBe HttpStatusCode.OK

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val contributors = body["topContributors"]?.jsonArray
            contributors?.size shouldBe 2
        }
    }

    test("report includes isPartial flag") {
        val partialNode = firmNodeWithUtilisation().copy(isPartial = true, missingBooks = listOf("book-x"))
        coEvery { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") } returns partialNode

        testApp {
            val response = client.post("/api/v1/risk/reports/cro")
            response.status shouldBe HttpStatusCode.OK

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["isPartial"]?.jsonPrimitive?.content shouldBe "true"
        }
    }

    // HIER-08: CRO report includes a generatedAt timestamp so consumers can assess data freshness
    test("report includes generatedAt timestamp") {
        coEvery { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") } returns
            firmNodeWithUtilisation()

        testApp {
            val response = client.post("/api/v1/risk/reports/cro")
            response.status shouldBe HttpStatusCode.OK

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.containsKey("generatedAt") shouldBe true
            body["generatedAt"]?.jsonPrimitive?.content?.isNotBlank() shouldBe true
        }
    }

    test("POST /api/v1/risk/reports/cro returns 503 when hierarchy aggregation fails") {
        coEvery { hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM") } throws
            RuntimeException("Cross-book VaR unavailable")

        testApp {
            val response = client.post("/api/v1/risk/reports/cro")
            response.status shouldBe HttpStatusCode.ServiceUnavailable
        }
    }
})
