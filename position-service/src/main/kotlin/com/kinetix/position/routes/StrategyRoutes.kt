package com.kinetix.position.routes

import com.kinetix.common.model.BookId
import com.kinetix.position.model.StrategyType
import com.kinetix.position.model.TradeStrategy
import com.kinetix.position.routes.dtos.CreateStrategyRequest
import com.kinetix.position.routes.dtos.StrategyResponse
import com.kinetix.position.service.TradeStrategyService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.strategyRoutes(strategyService: TradeStrategyService) {
    route("/api/v1/books/{bookId}/strategies") {

        post {
            val bookId = BookId(call.requirePathParam("bookId"))
            val request = call.receive<CreateStrategyRequest>()
            val strategyType = StrategyType.valueOf(request.strategyType)
            val strategy = strategyService.createStrategy(
                bookId = bookId,
                strategyType = strategyType,
                name = request.name,
            )
            call.respond(HttpStatusCode.Created, strategy.toResponse())
        }

        get {
            val bookId = BookId(call.requirePathParam("bookId"))
            val strategies = strategyService.listStrategies(bookId)
            call.respond(strategies.map { it.toResponse() })
        }

        route("/{strategyId}") {
            get {
                val strategyId = call.parameters["strategyId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                val strategy = strategyService.findById(strategyId)
                if (strategy == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(strategy.toResponse())
                }
            }
        }
    }
}

private fun TradeStrategy.toResponse() = StrategyResponse(
    strategyId = strategyId,
    bookId = bookId.value,
    strategyType = strategyType.name,
    name = name,
    createdAt = createdAt.toString(),
)
