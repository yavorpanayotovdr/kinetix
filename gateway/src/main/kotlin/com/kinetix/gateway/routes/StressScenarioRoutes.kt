package com.kinetix.gateway.routes

import com.kinetix.gateway.auth.requireUserId
import com.kinetix.gateway.client.ApproveScenarioParams
import com.kinetix.gateway.client.CreateScenarioParams
import com.kinetix.gateway.client.RegulatoryServiceClient
import com.kinetix.gateway.dto.CreateScenarioRequest
import com.kinetix.gateway.dto.toResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.stressScenarioRoutes(client: RegulatoryServiceClient) {
    route("/api/v1/stress-scenarios") {
        get({
            summary = "List all stress scenarios"
            tags = listOf("Stress Scenarios")
        }) {
            val scenarios = client.listScenarios()
            call.respond(scenarios.map { it.toResponse() })
        }

        get("/approved", {
            summary = "List approved stress scenarios"
            tags = listOf("Stress Scenarios")
        }) {
            val scenarios = client.listApprovedScenarios()
            call.respond(scenarios.map { it.toResponse() })
        }

        post({
            summary = "Create a new stress scenario"
            tags = listOf("Stress Scenarios")
        }) {
            val request = call.receive<CreateScenarioRequest>()
            val createdBy = call.requireUserId()
            val params = CreateScenarioParams(
                name = request.name,
                description = request.description,
                shocks = request.shocks,
                createdBy = createdBy,
            )
            val scenario = client.createScenario(params)
            call.respond(HttpStatusCode.Created, scenario.toResponse())
        }

        patch("/{id}/submit", {
            summary = "Submit scenario for approval"
            tags = listOf("Stress Scenarios")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.requirePathParam("id")
            val scenario = client.submitForApproval(id)
            call.respond(scenario.toResponse())
        }

        patch("/{id}/approve", {
            summary = "Approve a stress scenario"
            tags = listOf("Stress Scenarios")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.requirePathParam("id")
            val approvedBy = call.requireUserId()
            val params = ApproveScenarioParams(approvedBy = approvedBy)
            val scenario = client.approve(id, params)
            call.respond(scenario.toResponse())
        }

        patch("/{id}/retire", {
            summary = "Retire a stress scenario"
            tags = listOf("Stress Scenarios")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.requirePathParam("id")
            val scenario = client.retire(id)
            call.respond(scenario.toResponse())
        }
    }
}
