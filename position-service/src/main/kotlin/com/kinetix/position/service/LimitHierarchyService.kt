package com.kinetix.position.service

import com.kinetix.position.model.LimitCheckResult
import com.kinetix.position.model.LimitCheckStatus
import com.kinetix.position.model.LimitDefinition
import com.kinetix.position.model.LimitLevel
import com.kinetix.position.model.LimitType
import com.kinetix.position.persistence.LimitDefinitionRepository
import com.kinetix.position.persistence.TemporaryLimitIncreaseRepository
import java.math.BigDecimal
import java.time.Instant

class LimitHierarchyService(
    private val limitDefinitionRepo: LimitDefinitionRepository,
    private val temporaryLimitIncreaseRepo: TemporaryLimitIncreaseRepository,
    private val warningThresholdPct: Double = 0.8,
) {
    suspend fun checkLimit(
        entityId: String,
        level: LimitLevel,
        limitType: LimitType,
        currentExposure: BigDecimal,
        intraday: Boolean = true,
        parentEntityIds: Map<LimitLevel, String> = emptyMap(),
        parentExposures: Map<LimitLevel, BigDecimal> = emptyMap(),
    ): LimitCheckResult {
        val now = Instant.now()

        val entityLimit = limitDefinitionRepo.findByEntityAndType(entityId, level, limitType)
        if (entityLimit != null) {
            val result = evaluateLimit(entityLimit, currentExposure, intraday, now)
            if (result.status == LimitCheckStatus.BREACHED) {
                return result
            }
        }

        val hierarchyOrder = listOf(LimitLevel.DESK, LimitLevel.FIRM)
        for (parentLevel in hierarchyOrder) {
            if (parentLevel == level) continue
            if (parentLevel.ordinal >= level.ordinal && level != LimitLevel.TRADER) continue

            val parentEntityId = parentEntityIds[parentLevel]
                ?: if (parentLevel == LimitLevel.FIRM) "FIRM" else continue
            val parentExposure = parentExposures[parentLevel] ?: currentExposure

            val parentLimit = limitDefinitionRepo.findByEntityAndType(parentEntityId, parentLevel, limitType)
            if (parentLimit != null) {
                val parentResult = evaluateLimit(parentLimit, parentExposure, intraday, now)
                if (parentResult.status == LimitCheckStatus.BREACHED) {
                    return parentResult.copy(breachedAt = parentLevel)
                }
            }
        }

        if (entityLimit != null) {
            val result = evaluateLimit(entityLimit, currentExposure, intraday, now)
            return result
        }

        return LimitCheckResult(
            status = LimitCheckStatus.OK,
            currentExposure = currentExposure,
        )
    }

    private suspend fun evaluateLimit(
        limit: LimitDefinition,
        exposure: BigDecimal,
        intraday: Boolean,
        asOf: Instant,
    ): LimitCheckResult {
        val tempIncrease = temporaryLimitIncreaseRepo.findActiveByLimitId(limit.id, asOf)

        val baseLimit = when {
            intraday && limit.intradayLimit != null -> limit.intradayLimit
            !intraday && limit.overnightLimit != null -> limit.overnightLimit
            else -> limit.limitValue
        }

        val effectiveLimit = tempIncrease?.newValue ?: baseLimit
        val warningThreshold = effectiveLimit.multiply(BigDecimal.valueOf(warningThresholdPct))

        return when {
            exposure > effectiveLimit -> LimitCheckResult(
                status = LimitCheckStatus.BREACHED,
                limitValue = baseLimit,
                effectiveLimit = effectiveLimit,
                currentExposure = exposure,
                breachedAt = limit.level,
                message = "Exposure $exposure exceeds limit $effectiveLimit at ${limit.level} level",
            )

            exposure >= warningThreshold -> LimitCheckResult(
                status = LimitCheckStatus.WARNING,
                limitValue = baseLimit,
                effectiveLimit = effectiveLimit,
                currentExposure = exposure,
                breachedAt = limit.level,
                message = "Exposure $exposure approaching limit $effectiveLimit at ${limit.level} level",
            )

            else -> LimitCheckResult(
                status = LimitCheckStatus.OK,
                limitValue = baseLimit,
                effectiveLimit = effectiveLimit,
                currentExposure = exposure,
            )
        }
    }
}
