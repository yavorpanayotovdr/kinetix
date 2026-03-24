package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.risk.cache.VaRCache
import com.kinetix.risk.client.HierarchyDataClient
import com.kinetix.risk.model.BookHierarchyEntry
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import com.kinetix.risk.model.CrossBookVaRRequest
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.model.HierarchyNodeRisk
import com.kinetix.risk.model.HierarchyRiskSnapshot
import com.kinetix.risk.model.RiskContributor
import com.kinetix.risk.persistence.RiskHierarchySnapshotRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant

/**
 * Resolves the hierarchy tree, runs cross-book VaR aggregation at each level,
 * computes marginal contributions, and persists snapshots.
 *
 * Partial aggregation: if some books have no cached VaR, the result is marked
 * partial and those books are listed in [HierarchyNodeRisk.missingBooks].
 */
class HierarchyRiskService(
    private val hierarchyDataClient: HierarchyDataClient,
    private val crossBookVaRService: CrossBookVaRCalculationService,
    private val snapshotRepository: RiskHierarchySnapshotRepository,
    private val varCache: VaRCache,
    private val budgetUtilisationService: BudgetUtilisationService? = null,
    private val topContributorsLimit: Int = 5,
) {
    private val logger = LoggerFactory.getLogger(HierarchyRiskService::class.java)

    /**
     * Compute and return the hierarchy-level risk node for the given level and entity.
     * Returns null only when the entity itself cannot be resolved.
     */
    suspend fun aggregateHierarchy(level: HierarchyLevel, entityId: String): HierarchyNodeRisk? {
        val allMappings = hierarchyDataClient.getAllBookMappings()
        val allDesks = hierarchyDataClient.getAllDesks()
        val allDivisions = hierarchyDataClient.getAllDivisions()

        val entityName = when (level) {
            HierarchyLevel.FIRM -> "FIRM"
            HierarchyLevel.DIVISION -> allDivisions.find { it.id.value == entityId }?.name ?: entityId
            HierarchyLevel.DESK -> allDesks.find { it.id.value == entityId }?.name ?: entityId
            HierarchyLevel.BOOK -> entityId
        }

        val parentId = when (level) {
            HierarchyLevel.FIRM -> null
            HierarchyLevel.DIVISION -> "FIRM"
            HierarchyLevel.DESK -> allDesks.find { it.id.value == entityId }?.divisionId?.value
            HierarchyLevel.BOOK -> allMappings.find { it.bookId == entityId }?.deskId
        }

        // Collect the books under this entity
        val booksUnder = resolveBooksUnder(level, entityId, allMappings, allDesks, allDivisions)

        if (booksUnder.isEmpty()) {
            logger.info("No books under {} {}, returning zero VaR node", level, entityId)
            val emptyNode = HierarchyNodeRisk(
                level = level,
                entityId = entityId,
                entityName = entityName,
                parentId = parentId,
                varValue = 0.0,
                expectedShortfall = null,
                pnlToday = null,
                limitUtilisation = null,
                marginalVar = null,
                incrementalVar = null,
                topContributors = emptyList(),
                childCount = 0,
                isPartial = false,
                missingBooks = emptyList(),
            )
            persistSnapshot(emptyNode)
            return emptyNode
        }

        // Identify books with and without cached VaR
        val (booksWithVar, missingBooks) = booksUnder.partition { varCache.get(it.bookId) != null }
        val isPartial = missingBooks.isNotEmpty()

        if (isPartial) {
            logger.warn(
                "Partial hierarchy aggregation for {} {}: {} books missing VaR — {}",
                level, entityId, missingBooks.size, missingBooks.map { it.bookId },
            )
        }

        val effectiveBooks = if (booksWithVar.isEmpty()) {
            // No books have VaR at all; return zero partial result
            return HierarchyNodeRisk(
                level = level,
                entityId = entityId,
                entityName = entityName,
                parentId = parentId,
                varValue = 0.0,
                expectedShortfall = null,
                pnlToday = null,
                limitUtilisation = null,
                marginalVar = null,
                incrementalVar = null,
                topContributors = emptyList(),
                childCount = booksUnder.size,
                isPartial = true,
                missingBooks = missingBooks.map { it.bookId },
            )
        } else {
            booksWithVar
        }

        // Run cross-book VaR aggregation
        val bookIds = effectiveBooks.map { BookId(it.bookId) }
        val crossBookRequest = CrossBookVaRRequest(
            bookIds = bookIds,
            portfolioGroupId = "hierarchy-${level.name.lowercase()}-$entityId",
            calculationType = CalculationType.PARAMETRIC,
            confidenceLevel = ConfidenceLevel.CL_95,
        )

        val crossBookResult = try {
            crossBookVaRService.calculate(crossBookRequest)
        } catch (e: Exception) {
            logger.error("Cross-book VaR calculation failed for {} {}", level, entityId, e)
            null
        }

        val aggregateVar = crossBookResult?.varValue ?: 0.0
        val aggregateEs = crossBookResult?.expectedShortfall

        val limitUtilisation = budgetUtilisationService?.let { svc ->
            try {
                svc.computeUtilisation(level, entityId, BigDecimal(aggregateVar))?.utilisationPct?.toDouble()
            } catch (e: Exception) {
                logger.warn("Budget utilisation check failed for {} {}: {}", level, entityId, e.message)
                null
            }
        }

        // Build top contributors: per-book VaR contributions sorted by |contribution| desc
        val contributors = crossBookResult?.bookContributions
            ?.sortedByDescending { Math.abs(it.varContribution) }
            ?.take(topContributorsLimit)
            ?.map { contribution ->
                val bookEntry = effectiveBooks.find { it.bookId == contribution.bookId.value }
                val name = bookEntry?.bookName ?: contribution.bookId.value
                RiskContributor(
                    entityId = contribution.bookId.value,
                    entityName = name,
                    varContribution = contribution.varContribution,
                    pctOfTotal = contribution.percentageOfTotal,
                )
            } ?: emptyList()

        val childCount = when (level) {
            HierarchyLevel.FIRM -> allDivisions.size
            HierarchyLevel.DIVISION -> allDesks.count { it.divisionId.value == entityId }
            HierarchyLevel.DESK, HierarchyLevel.BOOK -> effectiveBooks.size
        }

        // Marginal VaR for this hierarchy node = sum of all book marginal VaRs under it.
        // marginalVar is additive: it measures the aggregate marginal contribution of
        // this entity's books to the portfolio VaR.
        val aggregateMarginalVar = crossBookResult?.bookContributions
            ?.sumOf { it.marginalVar }
            ?.takeIf { crossBookResult.bookContributions.isNotEmpty() }
        val aggregateIncrementalVar = crossBookResult?.bookContributions
            ?.sumOf { it.incrementalVar }
            ?.takeIf { crossBookResult.bookContributions.isNotEmpty() }

        val node = HierarchyNodeRisk(
            level = level,
            entityId = entityId,
            entityName = entityName,
            parentId = parentId,
            varValue = aggregateVar,
            expectedShortfall = aggregateEs,
            pnlToday = null,
            limitUtilisation = limitUtilisation,
            marginalVar = aggregateMarginalVar,
            incrementalVar = aggregateIncrementalVar,
            topContributors = contributors,
            childCount = childCount,
            isPartial = isPartial,
            missingBooks = missingBooks.map { it.bookId },
        )

        persistSnapshot(node)
        return node
    }

    private suspend fun persistSnapshot(node: HierarchyNodeRisk) {
        try {
            snapshotRepository.save(
                HierarchyRiskSnapshot(
                    snapshotAt = Instant.now(),
                    level = node.level,
                    entityId = node.entityId,
                    parentId = node.parentId,
                    varValue = node.varValue,
                    expectedShortfall = node.expectedShortfall,
                    pnlToday = node.pnlToday,
                    limitUtilisation = node.limitUtilisation,
                    marginalVar = node.marginalVar,
                    topContributors = node.topContributors,
                    isPartial = node.isPartial,
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to persist hierarchy risk snapshot for {} {}", node.level, node.entityId, e)
        }
    }

    private fun resolveBooksUnder(
        level: HierarchyLevel,
        entityId: String,
        allMappings: List<BookHierarchyEntry>,
        allDesks: List<com.kinetix.common.model.Desk>,
        allDivisions: List<com.kinetix.common.model.Division>,
    ): List<BookHierarchyEntry> = when (level) {
        HierarchyLevel.FIRM -> allMappings
        HierarchyLevel.DIVISION -> {
            val desksInDivision = allDesks.filter { it.divisionId.value == entityId }.map { it.id.value }.toSet()
            allMappings.filter { it.deskId in desksInDivision }
        }
        HierarchyLevel.DESK -> allMappings.filter { it.deskId == entityId }
        HierarchyLevel.BOOK -> allMappings.filter { it.bookId == entityId }
    }
}
