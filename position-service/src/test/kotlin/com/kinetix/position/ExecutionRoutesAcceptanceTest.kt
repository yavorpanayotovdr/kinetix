package com.kinetix.position

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.BookId
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.Position
import com.kinetix.common.model.Side
import com.kinetix.position.fix.ExecutionCostAnalysis
import com.kinetix.position.fix.ExecutionCostMetrics
import com.kinetix.position.fix.ExecutionCostRepository
import com.kinetix.position.fix.PrimeBrokerReconciliation
import com.kinetix.position.fix.PrimeBrokerReconciliationRepository
import com.kinetix.position.fix.PrimeBrokerReconciliationService
import com.kinetix.position.fix.ReconciliationBreak
import com.kinetix.position.fix.ReconciliationBreakStatus
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.routes.executionRoutes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.request.patch
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency as JCurrency

@Serializable
private data class ExecutionErrorBody(val error: String, val message: String)

private fun Application.configureTestApp(
    costRepo: ExecutionCostRepository,
    reconRepo: PrimeBrokerReconciliationRepository,
    reconService: PrimeBrokerReconciliationService,
    positionRepo: PositionRepository,
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ExecutionErrorBody("bad_request", cause.message ?: ""))
        }
    }
    routing {
        executionRoutes(costRepo, reconRepo, reconService, positionRepo)
    }
}

class ExecutionRoutesAcceptanceTest : FunSpec({

    val costRepo = mockk<ExecutionCostRepository>()
    val reconRepo = mockk<PrimeBrokerReconciliationRepository>(relaxed = true)
    val reconService = PrimeBrokerReconciliationService()
    val positionRepo = mockk<PositionRepository>()

    test("GET /api/v1/execution/cost/{bookId} returns list of cost analyses") {
        val analysis = ExecutionCostAnalysis(
            orderId = "ord-1",
            bookId = "book-1",
            instrumentId = "AAPL",
            completedAt = Instant.parse("2026-03-24T15:00:00Z"),
            arrivalPrice = BigDecimal("150.00"),
            averageFillPrice = BigDecimal("150.15"),
            side = Side.BUY,
            totalQty = BigDecimal("100"),
            metrics = ExecutionCostMetrics(
                slippageBps = BigDecimal("10.00"),
                marketImpactBps = null,
                timingCostBps = null,
                totalCostBps = BigDecimal("10.00"),
            ),
        )
        coEvery { costRepo.findByBookId("book-1") } returns listOf(analysis)

        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.get("/api/v1/execution/cost/book-1")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["orderId"]!!.jsonPrimitive.content shouldBe "ord-1"
            body[0].jsonObject["slippageBps"]!!.jsonPrimitive.content shouldBe "10.00"
        }
    }

    test("GET /api/v1/execution/cost/{bookId} returns empty list when no analyses exist") {
        coEvery { costRepo.findByBookId("book-empty") } returns emptyList()

        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.get("/api/v1/execution/cost/book-empty")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 0
        }
    }

    test("GET /api/v1/execution/reconciliation/{bookId} returns reconciliation history") {
        val recon = PrimeBrokerReconciliation(
            reconciliationDate = "2026-03-24",
            bookId = "book-2",
            status = "CLEAN",
            totalPositions = 5,
            matchedCount = 5,
            breakCount = 0,
            breaks = emptyList(),
            reconciledAt = Instant.parse("2026-03-24T18:00:00Z"),
        )
        coEvery { reconRepo.findByBookId("book-2") } returns listOf(recon)

        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.get("/api/v1/execution/reconciliation/book-2")
            response.status shouldBe HttpStatusCode.OK
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            body.size shouldBe 1
            body[0].jsonObject["status"]!!.jsonPrimitive.content shouldBe "CLEAN"
            body[0].jsonObject["breakCount"]!!.jsonPrimitive.content shouldBe "0"
        }
    }

    test("POST /api/v1/execution/reconciliation/{bookId}/statements returns reconciliation result") {
        coEvery { positionRepo.findByBookId(BookId("book-3")) } returns listOf(
            Position(
                bookId = BookId("book-3"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("100"),
                averageCost = Money(BigDecimal("150.00"), JCurrency.getInstance("USD")),
                marketPrice = Money(BigDecimal("155.00"), JCurrency.getInstance("USD")),
            )
        )

        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.post("/api/v1/execution/reconciliation/book-3/statements") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "bookId": "book-3",
                        "date": "2026-03-24",
                        "positions": [
                            {"instrumentId": "AAPL", "quantity": "100", "price": "155.00"}
                        ]
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.Created
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]!!.jsonPrimitive.content shouldBe "CLEAN"
            body["matchedCount"]!!.jsonPrimitive.content shouldBe "1"
            body["breakCount"]!!.jsonPrimitive.content shouldBe "0"
        }
    }

    // EXEC-04: break status update endpoint
    test("PATCH /api/v1/execution/reconciliation-breaks/{id}/{instrument}/status updates break status") {
        coEvery { reconRepo.updateBreakStatus("recon-1", "AAPL", ReconciliationBreakStatus.INVESTIGATING) } returns Unit

        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.patch("/api/v1/execution/reconciliation-breaks/recon-1/AAPL/status") {
                contentType(ContentType.Application.Json)
                setBody("""{"status": "INVESTIGATING"}""")
            }
            response.status shouldBe HttpStatusCode.NoContent
            coVerify(exactly = 1) { reconRepo.updateBreakStatus("recon-1", "AAPL", ReconciliationBreakStatus.INVESTIGATING) }
        }
    }

    test("PATCH break status with invalid status returns 400") {
        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.patch("/api/v1/execution/reconciliation-breaks/recon-1/AAPL/status") {
                contentType(ContentType.Application.Json)
                setBody("""{"status": "INVALID_STATUS"}""")
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST statement with book mismatch returns 400") {
        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.post("/api/v1/execution/reconciliation/book-4/statements") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "bookId": "DIFFERENT-BOOK",
                        "date": "2026-03-24",
                        "positions": []
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST statement detects material break when PB position differs by more than 1 unit") {
        coEvery { positionRepo.findByBookId(BookId("book-5")) } returns listOf(
            Position(
                bookId = BookId("book-5"),
                instrumentId = InstrumentId("AAPL"),
                assetClass = AssetClass.EQUITY,
                quantity = BigDecimal("105"),
                averageCost = Money(BigDecimal("150.00"), JCurrency.getInstance("USD")),
                marketPrice = Money(BigDecimal("155.00"), JCurrency.getInstance("USD")),
            )
        )

        testApplication {
            application { configureTestApp(costRepo, reconRepo, reconService, positionRepo) }
            val response = client.post("/api/v1/execution/reconciliation/book-5/statements") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "bookId": "book-5",
                        "date": "2026-03-24",
                        "positions": [
                            {"instrumentId": "AAPL", "quantity": "100", "price": "155.00"}
                        ]
                    }
                    """.trimIndent()
                )
            }
            response.status shouldBe HttpStatusCode.Created
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["status"]!!.jsonPrimitive.content shouldBe "BREAKS_FOUND"
            body["breakCount"]!!.jsonPrimitive.content shouldBe "1"
            response.bodyAsText() shouldContain "AAPL"
        }
    }
})
