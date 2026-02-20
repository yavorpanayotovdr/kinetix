package com.kinetix.gateway.routes

import com.kinetix.common.model.PortfolioId
import com.kinetix.gateway.client.PositionServiceClient
import com.kinetix.gateway.dto.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.positionRoutes(client: PositionServiceClient) {
    route("/api/v1/portfolios") {

        get {
            val portfolios = client.listPortfolios()
            call.respond(portfolios.map { it.toResponse() })
        }

        route("/{portfolioId}") {

            post("/trades") {
                val portfolioId = PortfolioId(call.parameters["portfolioId"]!!)
                val request = call.receive<BookTradeRequest>()
                val command = request.toCommand(portfolioId)
                val result = client.bookTrade(command)
                call.respond(HttpStatusCode.Created, result.toResponse())
            }

            get("/positions") {
                val portfolioId = PortfolioId(call.parameters["portfolioId"]!!)
                val positions = client.getPositions(portfolioId)
                call.respond(positions.map { it.toResponse() })
            }
        }
    }
}
