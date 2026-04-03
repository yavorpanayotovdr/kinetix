package com.kinetix.risk.service

import com.kinetix.risk.client.ClientResponse
import com.kinetix.risk.client.LimitServiceClient
import com.kinetix.risk.client.dtos.LimitDefinitionDto
import com.kinetix.risk.routes.dtos.StressLimitBreachDto
import com.kinetix.risk.routes.dtos.StressTestResponse
import java.math.BigDecimal

class StressLimitCheckService(
    private val limitServiceClient: LimitServiceClient,
    private val warningThresholdPct: Double = 0.8,
) {
    suspend fun evaluateBreaches(stressResponse: StressTestResponse): List<StressLimitBreachDto> {
        val limits = when (val response = limitServiceClient.getLimits()) {
            is ClientResponse.Success -> response.value.filter { it.active }
            else -> return emptyList()
        }

        if (limits.isEmpty()) return emptyList()

        val stressedVar = BigDecimal(stressResponse.stressedVar)
        val totalStressedNotional = stressResponse.positionImpacts
            .sumOf { BigDecimal(it.stressedMarketValue).abs() }

        return limits.mapNotNull { limit ->
            val stressedValue = when (limit.limitType) {
                "VAR" -> stressedVar
                "NOTIONAL" -> totalStressedNotional
                else -> return@mapNotNull null
            }
            evaluateLimit(limit, stressedValue, stressResponse.scenarioName)
        }
    }

    private fun evaluateLimit(
        limit: LimitDefinitionDto,
        stressedValue: BigDecimal,
        scenarioName: String,
    ): StressLimitBreachDto {
        val limitValue = BigDecimal(limit.limitValue)
        val warningThreshold = limitValue.multiply(BigDecimal.valueOf(warningThresholdPct))

        val severity = when {
            limitValue.signum() == 0 -> "OK"
            stressedValue > limitValue -> "BREACHED"
            stressedValue >= warningThreshold -> "WARNING"
            else -> "OK"
        }

        return StressLimitBreachDto(
            limitType = limit.limitType,
            limitLevel = limit.level,
            limitValue = limitValue.toPlainString(),
            stressedValue = stressedValue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
            breachSeverity = severity,
            scenarioName = scenarioName,
        )
    }
}
