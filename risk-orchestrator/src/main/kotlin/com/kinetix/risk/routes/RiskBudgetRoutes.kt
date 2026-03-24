package com.kinetix.risk.routes

import com.kinetix.risk.model.BudgetPeriod
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.RiskBudgetAllocation
import com.kinetix.risk.persistence.RiskBudgetAllocationRepository
import com.kinetix.risk.routes.dtos.RiskBudgetAllocationResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Serializable
private data class CreateRiskBudgetRequest(
    val entityLevel: String,
    val entityId: String,
    val budgetType: String,
    val budgetPeriod: String,
    val budgetAmount: String,
    val effectiveFrom: String,
    val effectiveTo: String? = null,
    val allocatedBy: String,
    val allocationNote: String? = null,
)

fun Route.riskBudgetRoutes(budgetRepository: RiskBudgetAllocationRepository) {
    route("/api/v1/risk/budgets") {
        get {
            val levelStr = call.request.queryParameters["level"]
            val entityId = call.request.queryParameters["entityId"]
            val level = levelStr?.let { str ->
                HierarchyLevel.entries.find { it.name.equals(str, ignoreCase = true) }
                    ?: run {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Unknown hierarchy level: $str",
                        )
                        return@get
                    }
            }
            val allocations = budgetRepository.findAll(level, entityId)
            call.respond(allocations.map { it.toResponse() })
        }

        post {
            val request = try {
                call.receive<CreateRiskBudgetRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request body: ${e.message}")
                return@post
            }

            val level = HierarchyLevel.entries.find { it.name.equals(request.entityLevel, ignoreCase = true) }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Unknown hierarchy level: ${request.entityLevel}")
                    return@post
                }
            val period = BudgetPeriod.entries.find { it.name.equals(request.budgetPeriod, ignoreCase = true) }
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Unknown budget period: ${request.budgetPeriod}")
                    return@post
                }
            val amount = request.budgetAmount.toBigDecimalOrNull()
                ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Invalid budgetAmount: ${request.budgetAmount}")
                    return@post
                }
            val effectiveFrom = runCatching { LocalDate.parse(request.effectiveFrom) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, "Invalid effectiveFrom date: ${request.effectiveFrom}")
                return@post
            }
            val effectiveTo = request.effectiveTo?.let {
                runCatching { LocalDate.parse(it) }.getOrElse {
                    call.respond(HttpStatusCode.BadRequest, "Invalid effectiveTo date: $it")
                    return@post
                }
            }

            val allocation = RiskBudgetAllocation(
                id = UUID.randomUUID().toString(),
                entityLevel = level,
                entityId = request.entityId,
                budgetType = request.budgetType,
                budgetPeriod = period,
                budgetAmount = amount,
                effectiveFrom = effectiveFrom,
                effectiveTo = effectiveTo,
                allocatedBy = request.allocatedBy,
                allocationNote = request.allocationNote,
            )

            budgetRepository.save(allocation)
            call.respond(HttpStatusCode.Created, allocation.toResponse())
        }

        route("/{id}") {
            get {
                val id = call.requirePathParam("id")
                val allocation = budgetRepository.findById(id)
                if (allocation == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(allocation.toResponse())
                }
            }

            delete {
                val id = call.requirePathParam("id")
                val existing = budgetRepository.findById(id)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    budgetRepository.delete(id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun RiskBudgetAllocation.toResponse() = RiskBudgetAllocationResponse(
    id = id,
    entityLevel = entityLevel.name,
    entityId = entityId,
    budgetType = budgetType,
    budgetPeriod = budgetPeriod.name,
    budgetAmount = budgetAmount.toPlainString(),
    effectiveFrom = effectiveFrom.toString(),
    effectiveTo = effectiveTo?.toString(),
    allocatedBy = allocatedBy,
    allocationNote = allocationNote,
)
