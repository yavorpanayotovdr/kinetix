package com.kinetix.risk.service

import com.kinetix.risk.model.BreachStatus
import com.kinetix.risk.model.BudgetUtilisation
import com.kinetix.risk.model.HierarchyLevel
import com.kinetix.risk.persistence.RiskBudgetAllocationRepository
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

/**
 * Computes VaR budget utilisation for a given entity level and ID.
 *
 * Thresholds are expressed as fractions of the budget amount:
 *  - WARNING at [warningThreshold] (default 0.80 = 80%)
 *  - BREACH at [breachThreshold] (default 1.00 = 100%)
 *
 * Returns null when no active budget is configured for the entity.
 */
class BudgetUtilisationService(
    private val budgetRepository: RiskBudgetAllocationRepository,
    private val warningThreshold: Double = 0.80,
    private val breachThreshold: Double = 1.00,
) {
    private val logger = LoggerFactory.getLogger(BudgetUtilisationService::class.java)

    suspend fun computeUtilisation(
        level: HierarchyLevel,
        entityId: String,
        currentVar: BigDecimal,
    ): BudgetUtilisation? {
        val allocation = budgetRepository.findEffective(
            level = level,
            entityId = entityId,
            budgetType = "VAR_BUDGET",
            asOf = LocalDate.now(),
        )

        if (allocation == null) {
            logger.debug("No active VAR_BUDGET for {} {}, skipping utilisation computation", level, entityId)
            return null
        }

        val budgetAmount = allocation.budgetAmount
        val utilisationPct = if (budgetAmount.compareTo(BigDecimal.ZERO) != 0) {
            currentVar.divide(budgetAmount, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal("0.00")
        }

        val utilisationRatio = utilisationPct.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP).toDouble()

        val breachStatus = when {
            utilisationRatio >= breachThreshold -> BreachStatus.BREACH
            utilisationRatio >= warningThreshold -> BreachStatus.WARNING
            else -> BreachStatus.WITHIN_BUDGET
        }

        if (breachStatus != BreachStatus.WITHIN_BUDGET) {
            logger.warn(
                "Budget utilisation {} for {} {}: currentVar={}, budget={}, utilisation={}%",
                breachStatus, level, entityId, currentVar, budgetAmount, utilisationPct,
            )
        }

        return BudgetUtilisation(
            entityLevel = level,
            entityId = entityId,
            budgetType = allocation.budgetType,
            budgetAmount = budgetAmount,
            currentVar = currentVar,
            utilisationPct = utilisationPct,
            breachStatus = breachStatus,
            updatedAt = Instant.now(),
        )
    }
}
