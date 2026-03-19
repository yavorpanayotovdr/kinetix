package com.kinetix.gateway.routes

import com.kinetix.common.model.BookId
import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.dto.*
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.positionRoutes(client: PositionServiceClient) {
    route("/api/v1/books") {

        get({
            summary = "List all books"
            tags = listOf("Books")
        }) {
            val portfolios = client.listPortfolios()
            call.respond(portfolios.map { it.toResponse() })
        }

        route("/{bookId}") {

            route("/trades") {
                get({
                    summary = "Get trade history for a book"
                    tags = listOf("Trades")
                    request {
                        pathParameter<String>("bookId") { description = "Book identifier" }
                    }
                }) {
                    val bookId = BookId(call.requirePathParam("bookId"))
                    val trades = client.getTradeHistory(bookId)
                    call.respond(trades.map { it.toResponse() })
                }

                post({
                    summary = "Book a trade"
                    tags = listOf("Trades")
                    request {
                        pathParameter<String>("bookId") { description = "Book identifier" }
                    }
                }) {
                    val bookId = BookId(call.requirePathParam("bookId"))
                    val request = call.receive<BookTradeRequest>()
                    val command = request.toCommand(bookId)
                    val result = client.bookTrade(command)
                    call.respond(HttpStatusCode.Created, result.toResponse())
                }
            }

            route("/positions") {
                get({
                    summary = "Get positions for a book"
                    tags = listOf("Positions")
                    request {
                        pathParameter<String>("bookId") { description = "Book identifier" }
                    }
                }) {
                    val bookId = BookId(call.requirePathParam("bookId"))
                    val positions = client.getPositions(bookId)
                    call.respond(positions.map { it.toResponse() })
                }
            }

            route("/summary") {
                get({
                    summary = "Get book summary with multi-currency aggregation"
                    tags = listOf("Books")
                    request {
                        pathParameter<String>("bookId") { description = "Book identifier" }
                        queryParameter<String>("baseCurrency") {
                            description = "Base currency for aggregation (default: USD)"
                            required = false
                        }
                    }
                }) {
                    val bookId = BookId(call.requirePathParam("bookId"))
                    val baseCurrency = call.request.queryParameters["baseCurrency"] ?: "USD"
                    val summary = client.getBookSummary(bookId, baseCurrency)
                    call.respond(summary.toResponse())
                }
            }
        }
    }
}
