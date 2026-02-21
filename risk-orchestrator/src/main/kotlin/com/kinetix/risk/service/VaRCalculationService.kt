package com.kinetix.risk.service

import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.kafka.RiskResultPublisher
import com.kinetix.risk.model.VaRCalculationRequest
import com.kinetix.risk.model.VaRResult
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory

class VaRCalculationService(
    private val positionProvider: PositionProvider,
    private val riskEngineClient: RiskEngineClient,
    private val resultPublisher: RiskResultPublisher,
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
) {
    private val logger = LoggerFactory.getLogger(VaRCalculationService::class.java)

    suspend fun calculateVaR(request: VaRCalculationRequest): VaRResult? {
        val positions = positionProvider.getPositions(request.portfolioId)
        if (positions.isEmpty()) {
            logger.info("No positions found for portfolio {}, skipping VaR calculation", request.portfolioId.value)
            return null
        }

        logger.info(
            "Calculating {} VaR for portfolio {} with {} positions",
            request.calculationType, request.portfolioId.value, positions.size,
        )

        val timer = meterRegistry.timer("var.calculation.duration")
        val sample = io.micrometer.core.instrument.Timer.start(meterRegistry)

        val result = riskEngineClient.calculateVaR(request, positions)

        sample.stop(timer)
        meterRegistry.counter(
            "var.calculation.count",
            "calculationType", request.calculationType.name,
        ).increment()

        resultPublisher.publish(result)

        logger.info(
            "VaR calculation complete for portfolio {}: VaR={}, ES={}",
            request.portfolioId.value, result.varValue, result.expectedShortfall,
        )

        return result
    }
}
