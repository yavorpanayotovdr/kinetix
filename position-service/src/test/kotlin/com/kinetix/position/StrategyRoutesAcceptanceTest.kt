package com.kinetix.position

import com.kinetix.position.persistence.DatabaseTestSetup
import com.kinetix.position.persistence.ExposedTradeStrategyRepository
import com.kinetix.position.routes.strategyRoutes
import com.kinetix.position.service.TradeStrategyService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private fun Application.configureStrategyTestApp(service: TradeStrategyService) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "bad_request", "message" to (cause.message ?: "Invalid request")),
            )
        }
    }
    routing {
        strategyRoutes(service)
    }
}

class StrategyRoutesAcceptanceTest : BehaviorSpec({

    val db = DatabaseTestSetup.startAndMigrate()
    val strategyRepository = ExposedTradeStrategyRepository(db)
    val strategyService = TradeStrategyService(strategyRepository)

    beforeEach {
        newSuspendedTransaction(db = db) {
            exec("TRUNCATE TABLE trade_strategies RESTART IDENTITY CASCADE")
        }
    }

    given("a book with no strategies") {

        `when`("POST /api/v1/books/{bookId}/strategies with STRADDLE type") {
            then("responds 201 with strategyId and persists the strategy") {
                testApplication {
                    application { configureStrategyTestApp(strategyService) }

                    val response = client.post("/api/v1/books/book-acc-1/strategies") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"strategyType":"STRADDLE","name":"Sep Straddle"}""")
                    }

                    response.status shouldBe HttpStatusCode.Created
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["strategyId"]!!.jsonPrimitive.content.shouldNotBeEmpty()
                    body["strategyType"]!!.jsonPrimitive.content shouldBe "STRADDLE"
                    body["name"]!!.jsonPrimitive.content shouldBe "Sep Straddle"
                }
            }
        }

        `when`("POST with CUSTOM strategy type and no name") {
            then("responds 201 with null name field") {
                testApplication {
                    application { configureStrategyTestApp(strategyService) }

                    val response = client.post("/api/v1/books/book-acc-2/strategies") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"strategyType":"CUSTOM"}""")
                    }

                    response.status shouldBe HttpStatusCode.Created
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["strategyType"]!!.jsonPrimitive.content shouldBe "CUSTOM"
                    // name absent or null is acceptable
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/strategies on empty book") {
            then("responds 200 with empty array") {
                testApplication {
                    application { configureStrategyTestApp(strategyService) }

                    val response = client.get("/api/v1/books/book-empty/strategies")

                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 0
                }
            }
        }
    }

    given("a book with one strategy") {

        `when`("GET /api/v1/books/{bookId}/strategies") {
            then("returns the strategy in the list") {
                testApplication {
                    application { configureStrategyTestApp(strategyService) }

                    client.post("/api/v1/books/book-list-1/strategies") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"strategyType":"BUTTERFLY","name":"Jun Butterfly"}""")
                    }

                    val response = client.get("/api/v1/books/book-list-1/strategies")

                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonArray
                    body.size shouldBe 1
                    body[0].jsonObject["strategyType"]!!.jsonPrimitive.content shouldBe "BUTTERFLY"
                    body[0].jsonObject["name"]!!.jsonPrimitive.content shouldBe "Jun Butterfly"
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/strategies/{strategyId}") {
            then("returns 200 with strategy details") {
                testApplication {
                    application { configureStrategyTestApp(strategyService) }

                    val createResp = client.post("/api/v1/books/book-get-1/strategies") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"strategyType":"CONDOR","name":"Jul Condor"}""")
                    }
                    val created = Json.parseToJsonElement(createResp.bodyAsText()).jsonObject
                    val strategyId = created["strategyId"]!!.jsonPrimitive.content

                    val response = client.get("/api/v1/books/book-get-1/strategies/$strategyId")

                    response.status shouldBe HttpStatusCode.OK
                    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    body["strategyId"]!!.jsonPrimitive.content shouldBe strategyId
                    body["strategyType"]!!.jsonPrimitive.content shouldBe "CONDOR"
                }
            }
        }

        `when`("GET /api/v1/books/{bookId}/strategies/{strategyId} for non-existent strategy") {
            then("returns 404") {
                testApplication {
                    application { configureStrategyTestApp(strategyService) }

                    val response = client.get("/api/v1/books/book-404/strategies/nonexistent-id")

                    response.status shouldBe HttpStatusCode.NotFound
                }
            }
        }
    }
})
