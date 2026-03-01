package com.kinetix.gateway.routes

import com.kinetix.common.model.PortfolioId
import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.dto.*
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.positionRoutes(client: PositionServiceClient) {
    route("/api/v1/portfolios") {

        get({
            summary = "List all portfolios"
            tags = listOf("Portfolios")
        }) {
            val portfolios = client.listPortfolios()
            call.respond(portfolios.map { it.toResponse() })
        }

        route("/{portfolioId}") {

            get("/trades", {
                summary = "Get trade history for a portfolio"
                tags = listOf("Trades")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val trades = client.getTradeHistory(portfolioId)
                call.respond(trades.map { it.toResponse() })
            }

            post("/trades", {
                summary = "Book a trade"
                tags = listOf("Trades")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val request = call.receive<BookTradeRequest>()
                val command = request.toCommand(portfolioId)
                val result = client.bookTrade(command)
                call.respond(HttpStatusCode.Created, result.toResponse())
            }

            get("/positions", {
                summary = "Get positions for a portfolio"
                tags = listOf("Positions")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val positions = client.getPositions(portfolioId)
                call.respond(positions.map { it.toResponse() })
            }

            get("/summary", {
                summary = "Get portfolio summary with multi-currency aggregation"
                tags = listOf("Portfolios")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                    queryParameter<String>("baseCurrency") {
                        description = "Base currency for aggregation (default: USD)"
                        required = false
                    }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val baseCurrency = call.request.queryParameters["baseCurrency"] ?: "USD"
                val summary = client.getPortfolioSummary(portfolioId, baseCurrency)
                call.respond(summary.toResponse())
            }
        }
    }
}
