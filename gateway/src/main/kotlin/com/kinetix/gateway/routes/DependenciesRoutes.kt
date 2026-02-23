package com.kinetix.gateway.routes

import com.kinetix.gateway.client.RiskServiceClient
import com.kinetix.gateway.dto.DependenciesRequest
import com.kinetix.gateway.dto.toParams
import com.kinetix.gateway.dto.toResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.dependenciesRoutes(client: RiskServiceClient) {
    route("/api/v1/risk/dependencies/{portfolioId}") {
        post {
            val portfolioId = call.requirePathParam("portfolioId")
            val request = call.receive<DependenciesRequest>()
            val params = request.toParams(portfolioId)
            val result = client.discoverDependencies(params)
            if (result != null) {
                call.respond(result.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
