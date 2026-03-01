package com.kinetix.regulatory.stress

import com.kinetix.regulatory.stress.dto.ApproveScenarioRequest
import com.kinetix.regulatory.stress.dto.CreateScenarioRequest
import com.kinetix.regulatory.stress.dto.StressScenarioResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kinetix.regulatory.stress.StressScenarioRoutes")

fun Route.stressScenarioRoutes(service: StressScenarioService) {
    route("/api/v1/stress-scenarios") {
        get({
            summary = "List all stress scenarios"
            tags = listOf("Stress Testing")
        }) {
            val scenarios = service.listAll()
            call.respond(scenarios.map { it.toResponse() })
        }

        get("/approved", {
            summary = "List approved stress scenarios"
            tags = listOf("Stress Testing")
        }) {
            val scenarios = service.listApproved()
            call.respond(scenarios.map { it.toResponse() })
        }

        post({
            summary = "Create a new stress scenario"
            tags = listOf("Stress Testing")
        }) {
            val request = call.receive<CreateScenarioRequest>()
            logger.info("Creating stress scenario: name={}, createdBy={}", request.name, request.createdBy)
            val scenario = service.create(
                name = request.name,
                description = request.description,
                shocks = request.shocks,
                createdBy = request.createdBy,
            )
            logger.info("Stress scenario created: id={}, name={}", scenario.id, scenario.name)
            call.respond(HttpStatusCode.Created, scenario.toResponse())
        }

        patch("/{id}/submit", {
            summary = "Submit scenario for approval"
            tags = listOf("Stress Testing")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            logger.info("Stress scenario submitted for approval: id={}", id)
            val updated = service.submitForApproval(id)
            logger.info("Stress scenario pending approval: id={}, status={}", updated.id, updated.status)
            call.respond(updated.toResponse())
        }

        patch("/{id}/approve", {
            summary = "Approve a stress scenario"
            tags = listOf("Stress Testing")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            val request = call.receive<ApproveScenarioRequest>()
            logger.info("Stress scenario approval: id={}, approvedBy={}", id, request.approvedBy)
            val updated = service.approve(id, request.approvedBy)
            logger.info("Stress scenario approved: id={}, status={}", updated.id, updated.status)
            call.respond(updated.toResponse())
        }

        patch("/{id}/retire", {
            summary = "Retire a stress scenario"
            tags = listOf("Stress Testing")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            logger.info("Stress scenario retirement: id={}", id)
            val updated = service.retire(id)
            logger.info("Stress scenario retired: id={}, status={}", updated.id, updated.status)
            call.respond(updated.toResponse())
        }
    }
}

private fun StressScenario.toResponse() = StressScenarioResponse(
    id = id,
    name = name,
    description = description,
    shocks = shocks,
    status = status.name,
    createdBy = createdBy,
    approvedBy = approvedBy,
    approvedAt = approvedAt?.toString(),
    createdAt = createdAt.toString(),
)
