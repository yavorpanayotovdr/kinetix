package com.kinetix.risk.routes

import com.kinetix.common.model.PortfolioId
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.margin.MarginCalculator
import com.kinetix.risk.routes.dtos.MarginEstimateResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.marginRoutes(
    positionProvider: PositionProvider,
    marginCalculator: MarginCalculator,
) {
    get("/api/v1/portfolios/{portfolioId}/margin", {
        summary = "Estimate margin requirements for a portfolio"
        tags = listOf("Margin")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            queryParameter<String>("previousMTM") {
                description = "Previous mark-to-market value for variation margin calculation"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) { body<MarginEstimateResponse>() }
        }
    }) {
        val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
        val previousMTM = call.request.queryParameters["previousMTM"]?.toBigDecimalOrNull()
        val positions = positionProvider.getPositions(portfolioId)

        val estimate = marginCalculator.calculate(positions, previousMTM)

        call.respond(
            MarginEstimateResponse(
                initialMargin = estimate.initialMargin.toPlainString(),
                variationMargin = estimate.variationMargin.toPlainString(),
                totalMargin = estimate.totalMargin.toPlainString(),
                currency = estimate.currency.currencyCode,
            )
        )
    }
}
