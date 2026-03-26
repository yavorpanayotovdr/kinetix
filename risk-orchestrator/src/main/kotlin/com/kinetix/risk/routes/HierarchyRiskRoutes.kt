package com.kinetix.risk.routes

import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.HierarchyNodeRisk
import com.kinetix.risk.model.RiskContributor
import com.kinetix.risk.routes.dtos.HierarchyNodeRiskResponse
import com.kinetix.risk.routes.dtos.RiskContributorResponse
import com.kinetix.risk.service.HierarchyRiskService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.hierarchyRiskRoutes(hierarchyRiskService: HierarchyRiskService) {
    get("/api/v1/risk/hierarchy/{level}/{entityId}") {
        val levelStr = call.requirePathParam("level")
        val level = levelStr.toHierarchyLevelOrNull()
            ?: run {
                call.respond(
                    HttpStatusCode.BadRequest,
                    "Unknown hierarchy level: $levelStr. Valid values: ${HierarchyLevel.entries.joinToString()}",
                )
                return@get
            }

        val entityId = call.requirePathParam("entityId")

        val node = hierarchyRiskService.aggregateHierarchy(level, entityId)

        if (node == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(node.toResponse())
        }
    }
}

private fun String.toHierarchyLevelOrNull(): HierarchyLevel? =
    HierarchyLevel.entries.find { it.name.equals(this, ignoreCase = true) }

private fun HierarchyNodeRisk.toResponse() = HierarchyNodeRiskResponse(
    level = level.name,
    entityId = entityId,
    entityName = entityName,
    parentId = parentId,
    varValue = "%.2f".format(varValue),
    expectedShortfall = expectedShortfall?.let { "%.2f".format(it) },
    pnlToday = pnlToday?.let { "%.2f".format(it) },
    limitUtilisation = limitUtilisation?.let { "%.2f".format(it) },
    marginalVar = marginalVar?.let { "%.6f".format(it) },
    incrementalVar = incrementalVar?.let { "%.2f".format(it) },
    topContributors = topContributors.map { it.toResponse() },
    childCount = childCount,
    isPartial = isPartial,
    missingBooks = missingBooks,
    generatedAt = Instant.now().toString(),
)

private fun RiskContributor.toResponse() = RiskContributorResponse(
    entityId = entityId,
    entityName = entityName,
    varContribution = "%.2f".format(varContribution),
    pctOfTotal = "%.2f".format(pctOfTotal),
)
