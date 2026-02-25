package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.StressTestRequest
import com.kinetix.gateway.dto.VaRCalculationRequest
import com.kinetix.gateway.dto.toParams
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.stressTestRoutes(client: RiskServiceClient) {
    route("/api/v1/risk/stress/{portfolioId}") {
        post({
            summary = "Run stress test"
            tags = listOf("Stress Tests")
            request {
                pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            }
        }) {
            val portfolioId = call.requirePathParam("portfolioId")
            val request = call.receive<StressTestRequest>()
            val params = request.toParams(portfolioId)
            val result = client.runStressTest(params)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    get("/api/v1/risk/stress/scenarios", {
        summary = "List stress test scenarios"
        tags = listOf("Stress Tests")
    }) {
        val scenarios = client.listScenarios()
        call.respond(scenarios)
    }

    route("/api/v1/risk/greeks/{portfolioId}") {
        post({
            summary = "Calculate Greeks"
            tags = listOf("Greeks")
            request {
                pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
            }
        }) {
            val portfolioId = call.requirePathParam("portfolioId")
            val request = call.receive<VaRCalculationRequest>()
            val params = request.toParams(portfolioId)
            val result = client.calculateGreeks(params)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
