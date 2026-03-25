package com.kinetix.regulatory.stress

import com.kinetix.regulatory.client.RiskOrchestratorClient
import com.kinetix.regulatory.stress.dto.ApproveScenarioRequest
import com.kinetix.regulatory.stress.dto.CreateScenarioRequest
import com.kinetix.regulatory.stress.dto.ReverseStressRequest
import com.kinetix.regulatory.stress.dto.RunStressTestRequest
import com.kinetix.regulatory.stress.dto.StressScenarioResponse
import com.kinetix.regulatory.stress.dto.StressTestResultResponse
import com.kinetix.regulatory.stress.dto.UpdateScenarioRequest
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.kinetix.regulatory.stress.StressScenarioRoutes")

fun Route.stressScenarioRoutes(
    service: StressScenarioService,
    riskOrchestratorClient: RiskOrchestratorClient? = null,
) {
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
                scenarioType = runCatching { ScenarioType.valueOf(request.scenarioType) }
                    .getOrDefault(ScenarioType.PARAMETRIC),
                category = runCatching { ScenarioCategory.valueOf(request.category) }
                    .getOrDefault(ScenarioCategory.INTERNAL_APPROVED),
                parentScenarioId = request.parentScenarioId,
                correlationOverride = request.correlationOverride,
                liquidityStressFactors = request.liquidityStressFactors,
                historicalPeriodId = request.historicalPeriodId,
                targetLoss = request.targetLoss?.let { java.math.BigDecimal(it) },
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

        post("/reverse-stress", {
            summary = "Find the minimum-norm shock vector that causes the target portfolio loss"
            tags = listOf("Stress Testing")
            request {
                body<ReverseStressRequest>()
            }
        }) {
            val client = riskOrchestratorClient
                ?: throw IllegalStateException("RiskOrchestratorClient not configured")
            val request = call.receive<ReverseStressRequest>()
            if (request.targetLoss <= 0.0) {
                throw IllegalArgumentException("targetLoss must be positive")
            }
            logger.info("Running reverse stress: bookId={}, targetLoss={}", request.bookId, request.targetLoss)
            val result = client.runReverseStress(
                bookId = request.bookId,
                targetLoss = request.targetLoss,
                maxShock = request.maxShock,
            )
            logger.info("Reverse stress complete: bookId={}, converged={}", request.bookId, result.converged)
            call.respond(HttpStatusCode.OK, result)
        }

        put("/{id}", {
            summary = "Update a stress scenario"
            tags = listOf("Stress Testing")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            val request = call.receive<UpdateScenarioRequest>()
            logger.info("Updating stress scenario: id={}", id)
            val updated = service.update(
                id = id,
                shocks = request.shocks,
                correlationOverride = request.correlationOverride,
                liquidityStressFactors = request.liquidityStressFactors,
            )
            logger.info("Stress scenario updated: id={}, version={}, status={}", updated.id, updated.version, updated.status)
            call.respond(updated.toResponse())
        }

        post("/{id}/run", {
            summary = "Run a stress scenario against a book"
            tags = listOf("Stress Testing")
            request {
                pathParameter<String>("id") { description = "Scenario identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            val request = call.receive<RunStressTestRequest>()
            logger.info("Running stress scenario: id={}, bookId={}", id, request.bookId)
            val result = service.runScenario(id, request.bookId, request.modelVersion)
            logger.info("Stress scenario run complete: id={}, bookId={}, pnlImpact={}", id, request.bookId, result.pnlImpact)
            call.respond(HttpStatusCode.Created, result.toResponse())
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
    scenarioType = scenarioType.name,
    category = category.name,
    version = version,
    parentScenarioId = parentScenarioId,
    correlationOverride = correlationOverride,
    liquidityStressFactors = liquidityStressFactors,
    historicalPeriodId = historicalPeriodId,
    targetLoss = targetLoss?.toPlainString(),
)

private fun StressTestResult.toResponse() = StressTestResultResponse(
    id = id,
    scenarioId = scenarioId,
    bookId = bookId,
    calculatedAt = calculatedAt.toString(),
    basePv = basePv?.toPlainString(),
    stressedPv = stressedPv?.toPlainString(),
    pnlImpact = pnlImpact?.toPlainString(),
    varImpact = varImpact?.toString(),
    positionImpacts = positionImpacts,
    modelVersion = modelVersion,
)
