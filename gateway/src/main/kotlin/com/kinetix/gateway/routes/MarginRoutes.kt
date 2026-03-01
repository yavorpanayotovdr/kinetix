package com.kinetix.gateway.routes

import com.kinetix.gateway.client.MarginEstimateSummary
import com.kinetix.gateway.client.RiskServiceClient
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class MarginEstimateDto(
    val initialMargin: String,
    val variationMargin: String,
    val totalMargin: String,
    val currency: String,
)

private fun MarginEstimateSummary.toDto() = MarginEstimateDto(
    initialMargin = initialMargin,
    variationMargin = variationMargin,
    totalMargin = totalMargin,
    currency = currency,
)

fun Route.marginRoutes(riskClient: RiskServiceClient) {
    get("/api/v1/portfolios/{portfolioId}/margin", {
        summary = "Get margin estimate for a portfolio"
        tags = listOf("Margin")
        request {
            pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            queryParameter<String>("previousMTM") {
                description = "Previous MTM for variation margin calculation"
                required = false
            }
        }
    }) {
        val portfolioId = call.requirePathParam("portfolioId")
        val previousMTM = call.request.queryParameters["previousMTM"]
        val result = riskClient.getMarginEstimate(portfolioId, previousMTM)
        if (result != null) {
            call.respond(result.toDto())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}
