package com.kinetix.risk.routes

import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.routes.dtos.HierarchyNodeRiskResponse
import com.kinetix.risk.routes.dtos.RiskContributorResponse
import com.kinetix.risk.service.HierarchyRiskService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("CroReportRoutes")

/**
 * POST /api/v1/risk/reports/cro
 *
 * Triggers a firm-level risk aggregation and returns a structured CRO report
 * containing VaR, expected shortfall, budget utilisation, and top contributors.
 */
fun Route.croReportRoutes(hierarchyRiskService: HierarchyRiskService) {
    post("/api/v1/risk/reports/cro") {
        val node = try {
            hierarchyRiskService.aggregateHierarchy(HierarchyLevel.FIRM, "FIRM")
        } catch (e: Exception) {
            logger.error("CRO report generation failed", e)
            call.respond(HttpStatusCode.ServiceUnavailable, "Risk aggregation unavailable: ${e.message}")
            return@post
        }

        if (node == null) {
            call.respond(HttpStatusCode.ServiceUnavailable, "No firm-level risk data available")
            return@post
        }

        val response = HierarchyNodeRiskResponse(
            level = node.level.name,
            entityId = node.entityId,
            entityName = node.entityName,
            parentId = node.parentId,
            varValue = "%.2f".format(node.varValue),
            expectedShortfall = node.expectedShortfall?.let { "%.2f".format(it) },
            pnlToday = node.pnlToday?.let { "%.2f".format(it) },
            limitUtilisation = node.limitUtilisation?.let { "%.2f".format(it) },
            marginalVar = node.marginalVar?.let { "%.6f".format(it) },
            incrementalVar = node.incrementalVar?.let { "%.2f".format(it) },
            topContributors = node.topContributors.map { c ->
                RiskContributorResponse(
                    entityId = c.entityId,
                    entityName = c.entityName,
                    varContribution = "%.2f".format(c.varContribution),
                    pctOfTotal = "%.2f".format(c.pctOfTotal),
                )
            },
            childCount = node.childCount,
            isPartial = node.isPartial,
            missingBooks = node.missingBooks,
            generatedAt = Instant.now().toString(),
        )

        call.respond(HttpStatusCode.OK, response)
    }
}
