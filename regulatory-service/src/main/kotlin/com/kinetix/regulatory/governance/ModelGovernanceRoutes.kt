package com.kinetix.regulatory.governance

import com.kinetix.regulatory.governance.dto.ModelVersionResponse
import com.kinetix.regulatory.governance.dto.RegisterModelRequest
import com.kinetix.regulatory.governance.dto.TransitionStatusRequest
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kinetix.regulatory.governance.ModelGovernanceRoutes")

fun Route.modelGovernanceRoutes(registry: ModelRegistry) {
    route("/api/v1/models") {
        get({
            summary = "List all model versions"
            tags = listOf("Model Governance")
        }) {
            val models = registry.listAll()
            call.respond(models.map { it.toResponse() })
        }

        post({
            summary = "Register a new model version"
            tags = listOf("Model Governance")
        }) {
            val request = call.receive<RegisterModelRequest>()
            logger.info("Registering model: name={}, version={}", request.modelName, request.version)
            val model = registry.register(
                modelName = request.modelName,
                version = request.version,
                parameters = request.parameters,
            )
            logger.info("Model registered: id={}, name={}, version={}", model.id, model.modelName, model.version)
            call.respond(HttpStatusCode.Created, model.toResponse())
        }

        patch("/{id}/status", {
            summary = "Transition model version status"
            tags = listOf("Model Governance")
            request {
                pathParameter<String>("id") { description = "Model version identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            val request = call.receive<TransitionStatusRequest>()
            val targetStatus = ModelVersionStatus.valueOf(request.targetStatus)
            logger.info("Model status transition: id={}, targetStatus={}, approvedBy={}", id, targetStatus, request.approvedBy)
            val updated = registry.transitionStatus(id, targetStatus, request.approvedBy)
            logger.info("Model status transitioned: id={}, newStatus={}", updated.id, updated.status)
            call.respond(updated.toResponse())
        }
    }
}

private fun ModelVersion.toResponse() = ModelVersionResponse(
    id = id,
    modelName = modelName,
    version = version,
    status = status.name,
    parameters = parameters,
    approvedBy = approvedBy,
    approvedAt = approvedAt?.toString(),
    createdAt = createdAt.toString(),
)
