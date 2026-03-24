package com.kinetix.risk.routes

import com.kinetix.risk.model.BudgetPeriod
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.RiskBudgetAllocation
import com.kinetix.risk.persistence.RiskBudgetAllocationRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true }

private fun sampleAllocation(id: String = "alloc-1") = RiskBudgetAllocation(
    id = id,
    entityLevel = HierarchyLevel.DESK,
    entityId = "desk-rates",
    budgetType = "VAR_BUDGET",
    budgetPeriod = BudgetPeriod.DAILY,
    budgetAmount = BigDecimal("5000000.00"),
    effectiveFrom = LocalDate.of(2026, 1, 1),
    effectiveTo = null,
    allocatedBy = "cro@firm.com",
    allocationNote = "Annual risk budget for rates desk",
)

class RiskBudgetRoutesAcceptanceTest : FunSpec({

    val budgetRepository = mockk<RiskBudgetAllocationRepository>()

    beforeEach { clearMocks(budgetRepository) }

    fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            install(ContentNegotiation) { json(json) }
            routing { riskBudgetRoutes(budgetRepository) }
            block()
        }
    }

    // ── GET /api/v1/risk/budgets ───────────────────────────────────────────────

    test("GET /api/v1/risk/budgets returns all allocations") {
        coEvery { budgetRepository.findAll(null, null) } returns listOf(sampleAllocation("alloc-1"), sampleAllocation("alloc-2"))

        testApp {
            val response = client.get("/api/v1/risk/budgets")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 2
            body[0].jsonObject["id"]!!.jsonPrimitive.content shouldBe "alloc-1"
        }
    }

    test("GET /api/v1/risk/budgets?level=DESK returns desk-level allocations") {
        coEvery { budgetRepository.findAll(HierarchyLevel.DESK, null) } returns listOf(sampleAllocation())

        testApp {
            val response = client.get("/api/v1/risk/budgets?level=DESK")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["entityLevel"]!!.jsonPrimitive.content shouldBe "DESK"
        }
    }

    // ── GET /api/v1/risk/budgets/{id} ─────────────────────────────────────────

    test("GET /api/v1/risk/budgets/{id} returns allocation when found") {
        coEvery { budgetRepository.findById("alloc-1") } returns sampleAllocation("alloc-1")

        testApp {
            val response = client.get("/api/v1/risk/budgets/alloc-1")

            response.status shouldBe HttpStatusCode.OK

            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["id"]!!.jsonPrimitive.content shouldBe "alloc-1"
            body["entityId"]!!.jsonPrimitive.content shouldBe "desk-rates"
            body["budgetType"]!!.jsonPrimitive.content shouldBe "VAR_BUDGET"
            body["budgetAmount"]!!.jsonPrimitive.content shouldBe "5000000.00"
        }
    }

    test("GET /api/v1/risk/budgets/{id} returns 404 when not found") {
        coEvery { budgetRepository.findById("nonexistent") } returns null

        testApp {
            val response = client.get("/api/v1/risk/budgets/nonexistent")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    // ── POST /api/v1/risk/budgets ──────────────────────────────────────────────

    test("POST /api/v1/risk/budgets creates allocation and returns 201") {
        coEvery { budgetRepository.save(any()) } returns Unit

        testApp {
            val response = client.post("/api/v1/risk/budgets") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                      "entityLevel":"DESK","entityId":"desk-rates",
                      "budgetType":"VAR_BUDGET","budgetPeriod":"DAILY",
                      "budgetAmount":"5000000.00","effectiveFrom":"2026-01-01",
                      "allocatedBy":"cro@firm.com"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.Created
            coVerify(exactly = 1) { budgetRepository.save(any()) }
        }
    }

    test("POST /api/v1/risk/budgets returns 400 when budgetAmount is missing") {
        testApp {
            val response = client.post("/api/v1/risk/budgets") {
                contentType(ContentType.Application.Json)
                setBody("""{"entityLevel":"DESK","entityId":"desk-rates","budgetType":"VAR_BUDGET"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    // ── DELETE /api/v1/risk/budgets/{id} ──────────────────────────────────────

    test("DELETE /api/v1/risk/budgets/{id} removes allocation and returns 204") {
        coEvery { budgetRepository.findById("alloc-1") } returns sampleAllocation("alloc-1")
        coEvery { budgetRepository.delete("alloc-1") } returns Unit

        testApp {
            val response = client.delete("/api/v1/risk/budgets/alloc-1")

            response.status shouldBe HttpStatusCode.NoContent
            coVerify(exactly = 1) { budgetRepository.delete("alloc-1") }
        }
    }

    test("DELETE /api/v1/risk/budgets/{id} returns 404 when not found") {
        coEvery { budgetRepository.findById("nonexistent") } returns null

        testApp {
            val response = client.delete("/api/v1/risk/budgets/nonexistent")

            response.status shouldBe HttpStatusCode.NotFound
        }
    }
})
