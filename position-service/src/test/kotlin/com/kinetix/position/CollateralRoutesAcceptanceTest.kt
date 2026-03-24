package com.kinetix.position

import com.kinetix.position.persistence.DatabaseTestSetup
import com.kinetix.position.persistence.ExposedCollateralBalanceRepository
import com.kinetix.position.routes.CollateralBalanceResponse
import com.kinetix.position.routes.NetCollateralResponse
import com.kinetix.position.routes.collateralRoutes
import com.kinetix.position.service.CollateralTrackingService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private fun Application.configureTestApp(service: CollateralTrackingService) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "invalid_request", "message" to (cause.message ?: "Invalid request")),
            )
        }
    }
    routing {
        collateralRoutes(service)
    }
}

class CollateralRoutesAcceptanceTest : FunSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val repository = ExposedCollateralBalanceRepository(db)
    val service = CollateralTrackingService(repository)

    beforeEach {
        newSuspendedTransaction(db = db) {
            exec("TRUNCATE TABLE collateral_balances RESTART IDENTITY CASCADE")
        }
    }

    test("POST /api/v1/counterparties/{id}/collateral creates CASH balance with zero haircut") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "collateralType": "CASH",
                        "amount": "1000000",
                        "currency": "USD",
                        "direction": "RECEIVED",
                        "asOfDate": "2024-01-15"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.Created
            val body = Json.decodeFromString<CollateralBalanceResponse>(response.bodyAsText())
            body.counterpartyId shouldBe "CP-001"
            body.collateralType shouldBe "CASH"
            body.amount shouldBe "1000000"
            body.currency shouldBe "USD"
            body.direction shouldBe "RECEIVED"
            body.asOfDate shouldBe "2024-01-15"
            // CASH haircut = 0%, so valueAfterHaircut == amount
            body.valueAfterHaircut shouldBe "1000000.00"
        }
    }

    test("POST /api/v1/counterparties/{id}/collateral applies 3% haircut to GOVERNMENT_BOND") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "collateralType": "GOVERNMENT_BOND",
                        "amount": "1000000",
                        "currency": "USD",
                        "direction": "RECEIVED"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.Created
            val body = Json.decodeFromString<CollateralBalanceResponse>(response.bodyAsText())
            // 1,000,000 * 0.97 = 970,000
            body.valueAfterHaircut shouldBe "970000.00"
        }
    }

    test("POST /api/v1/counterparties/{id}/collateral applies 10% haircut to CORPORATE_BOND") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "collateralType": "CORPORATE_BOND",
                        "amount": "500000",
                        "currency": "USD",
                        "direction": "POSTED"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.Created
            val body = Json.decodeFromString<CollateralBalanceResponse>(response.bodyAsText())
            // 500,000 * 0.90 = 450,000
            body.valueAfterHaircut shouldBe "450000.00"
        }
    }

    test("POST /api/v1/counterparties/{id}/collateral applies 20% haircut to EQUITY") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "collateralType": "EQUITY",
                        "amount": "250000",
                        "currency": "USD",
                        "direction": "RECEIVED"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.Created
            val body = Json.decodeFromString<CollateralBalanceResponse>(response.bodyAsText())
            // 250,000 * 0.80 = 200,000
            body.valueAfterHaircut shouldBe "200000.00"
        }
    }

    test("POST /api/v1/counterparties/{id}/collateral returns 400 for zero amount") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "collateralType": "CASH",
                        "amount": "0",
                        "currency": "USD",
                        "direction": "RECEIVED"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /api/v1/counterparties/{id}/collateral returns 400 for unknown collateral type") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "collateralType": "GOLD",
                        "amount": "1000000",
                        "currency": "USD",
                        "direction": "RECEIVED"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.bodyAsText() shouldContain "invalid_collateral_type"
        }
    }

    test("POST /api/v1/counterparties/{id}/collateral persists netting set ID") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody(
                    """
                    {
                        "nettingSetId": "NS-GS-001",
                        "collateralType": "CASH",
                        "amount": "500000",
                        "currency": "USD",
                        "direction": "RECEIVED"
                    }
                    """.trimIndent()
                )
            }

            response.status shouldBe HttpStatusCode.Created
            val body = Json.decodeFromString<CollateralBalanceResponse>(response.bodyAsText())
            body.nettingSetId shouldBe "NS-GS-001"
        }
    }

    test("GET /api/v1/counterparties/{id}/collateral returns all balances for that counterparty") {
        testApplication {
            application { configureTestApp(service) }

            // Seed two balances for CP-001 and one for CP-002
            client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody("""{"collateralType":"CASH","amount":"1000000","currency":"USD","direction":"RECEIVED"}""")
            }
            client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody("""{"collateralType":"GOVERNMENT_BOND","amount":"500000","currency":"USD","direction":"POSTED"}""")
            }
            client.post("/api/v1/counterparties/CP-002/collateral") {
                contentType(ContentType.Application.Json)
                setBody("""{"collateralType":"CASH","amount":"200000","currency":"USD","direction":"RECEIVED"}""")
            }

            val response = client.get("/api/v1/counterparties/CP-001/collateral")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<List<CollateralBalanceResponse>>(response.bodyAsText())
            body.size shouldBe 2
            body.all { it.counterpartyId == "CP-001" } shouldBe true
        }
    }

    test("GET /api/v1/counterparties/{id}/collateral returns empty list when no collateral posted") {
        testApplication {
            application { configureTestApp(service) }

            val response = client.get("/api/v1/counterparties/CP-NONE/collateral")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<List<CollateralBalanceResponse>>(response.bodyAsText())
            body.size shouldBe 0
        }
    }

    test("GET /api/v1/counterparties/{id}/collateral/net returns net collateral position") {
        testApplication {
            application { configureTestApp(service) }

            // Post 1M CASH received and 500K GOV_BOND posted (post-haircut: 485K)
            client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody("""{"collateralType":"CASH","amount":"1000000","currency":"USD","direction":"RECEIVED"}""")
            }
            client.post("/api/v1/counterparties/CP-001/collateral") {
                contentType(ContentType.Application.Json)
                setBody("""{"collateralType":"GOVERNMENT_BOND","amount":"500000","currency":"USD","direction":"POSTED"}""")
            }

            val response = client.get("/api/v1/counterparties/CP-001/collateral/net")

            response.status shouldBe HttpStatusCode.OK
            val body = Json.decodeFromString<NetCollateralResponse>(response.bodyAsText())
            body.counterpartyId shouldBe "CP-001"
            body.collateralReceived shouldBe "1000000.00"
            // 500,000 * 0.97 = 485,000
            body.collateralPosted shouldBe "485000.00"
        }
    }
})
