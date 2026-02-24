package com.kinetix.risk.service

import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.kafka.RiskResultPublisher
import com.kinetix.risk.model.*
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.json.Json
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class VaRCalculationService(
    private val positionProvider: PositionProvider,
    private val riskEngineClient: RiskEngineClient,
    private val resultPublisher: RiskResultPublisher,
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
    private val dependenciesDiscoverer: DependenciesDiscoverer? = null,
    private val marketDataFetcher: MarketDataFetcher? = null,
    private val runRecorder: CalculationRunRecorder = NoOpCalculationRunRecorder(),
) {
    private val logger = LoggerFactory.getLogger(VaRCalculationService::class.java)

    suspend fun calculateVaR(
        request: VaRCalculationRequest,
        triggerType: TriggerType = TriggerType.ON_DEMAND,
    ): VaRResult? {
        val runId = UUID.randomUUID()
        val runStartedAt = Instant.now()
        val steps = mutableListOf<PipelineStep>()
        var runError: String? = null

        try {
            // Step 1: Fetch positions
            val fetchPosStart = Instant.now()
            val positions = positionProvider.getPositions(request.portfolioId)
            val fetchPosDuration = java.time.Duration.between(fetchPosStart, Instant.now()).toMillis()
            steps.add(
                PipelineStep(
                    name = PipelineStepName.FETCH_POSITIONS,
                    status = RunStatus.COMPLETED,
                    startedAt = fetchPosStart,
                    completedAt = Instant.now(),
                    durationMs = fetchPosDuration,
                    details = mapOf(
                        "positionCount" to positions.size,
                        "positions" to Json.encodeToString(positions.map { pos ->
                            buildMap {
                                put("instrumentId", pos.instrumentId.value)
                                put("assetClass", pos.assetClass.name)
                                put("quantity", pos.quantity.toPlainString())
                                put("averageCost", "${pos.averageCost.amount} ${pos.averageCost.currency}")
                                put("marketPrice", "${pos.marketPrice.amount} ${pos.marketPrice.currency}")
                                put("marketValue", "${pos.marketValue.amount} ${pos.marketValue.currency}")
                                put("unrealizedPnl", "${pos.unrealizedPnl.amount} ${pos.unrealizedPnl.currency}")
                            }
                        }),
                    ),
                )
            )

            if (positions.isEmpty()) {
                logger.info("No positions found for portfolio {}, skipping VaR calculation", request.portfolioId.value)
                return null
            }

            logger.info(
                "Calculating {} VaR for portfolio {} with {} positions",
                request.calculationType, request.portfolioId.value, positions.size,
            )

            // Step 2: Discover dependencies
            val discoverStart = Instant.now()
            val dependencies = try {
                dependenciesDiscoverer?.discover(
                    positions,
                    request.calculationType.name,
                    request.confidenceLevel.name,
                ) ?: emptyList()
            } catch (e: Exception) {
                logger.warn("Market data dependency discovery failed, proceeding with defaults", e)
                emptyList()
            }
            val discoverDuration = java.time.Duration.between(discoverStart, Instant.now()).toMillis()
            val dataTypes = dependencies.mapNotNull { it.dataType }.distinct().joinToString(",")
            steps.add(
                PipelineStep(
                    name = PipelineStepName.DISCOVER_DEPENDENCIES,
                    status = RunStatus.COMPLETED,
                    startedAt = discoverStart,
                    completedAt = Instant.now(),
                    durationMs = discoverDuration,
                    details = mapOf(
                        "dependencyCount" to dependencies.size,
                        "dataTypes" to dataTypes,
                    ),
                )
            )

            // Step 3: Fetch market data
            val fetchMdStart = Instant.now()
            val marketData = try {
                if (dependencies.isNotEmpty() && marketDataFetcher != null) {
                    marketDataFetcher.fetch(dependencies)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                logger.warn("Market data fetch failed, proceeding with defaults", e)
                emptyList()
            }
            val fetchMdDuration = java.time.Duration.between(fetchMdStart, Instant.now()).toMillis()
            steps.add(
                PipelineStep(
                    name = PipelineStepName.FETCH_MARKET_DATA,
                    status = RunStatus.COMPLETED,
                    startedAt = fetchMdStart,
                    completedAt = Instant.now(),
                    durationMs = fetchMdDuration,
                    details = mapOf(
                        "requested" to dependencies.size,
                        "fetched" to marketData.size,
                    ),
                )
            )

            // Step 4: Calculate VaR
            val calcStart = Instant.now()
            val timer = meterRegistry.timer("var.calculation.duration")
            val sample = io.micrometer.core.instrument.Timer.start(meterRegistry)

            val result = riskEngineClient.calculateVaR(request, positions, marketData)

            sample.stop(timer)
            meterRegistry.counter(
                "var.calculation.count",
                "calculationType", request.calculationType.name,
            ).increment()

            val calcDuration = java.time.Duration.between(calcStart, Instant.now()).toMillis()
            steps.add(
                PipelineStep(
                    name = PipelineStepName.CALCULATE_VAR,
                    status = RunStatus.COMPLETED,
                    startedAt = calcStart,
                    completedAt = Instant.now(),
                    durationMs = calcDuration,
                    details = mapOf(
                        "varValue" to result.varValue,
                        "expectedShortfall" to result.expectedShortfall,
                    ),
                )
            )

            // Step 5: Publish result
            val publishStart = Instant.now()
            resultPublisher.publish(result)
            val publishDuration = java.time.Duration.between(publishStart, Instant.now()).toMillis()
            steps.add(
                PipelineStep(
                    name = PipelineStepName.PUBLISH_RESULT,
                    status = RunStatus.COMPLETED,
                    startedAt = publishStart,
                    completedAt = Instant.now(),
                    durationMs = publishDuration,
                    details = mapOf("topic" to "risk.results"),
                )
            )

            logger.info(
                "VaR calculation complete for portfolio {}: VaR={}, ES={}",
                request.portfolioId.value, result.varValue, result.expectedShortfall,
            )

            val runCompletedAt = Instant.now()
            val run = CalculationRun(
                runId = runId,
                portfolioId = request.portfolioId.value,
                triggerType = triggerType,
                status = RunStatus.COMPLETED,
                startedAt = runStartedAt,
                completedAt = runCompletedAt,
                durationMs = java.time.Duration.between(runStartedAt, runCompletedAt).toMillis(),
                calculationType = request.calculationType.name,
                confidenceLevel = request.confidenceLevel.name,
                varValue = result.varValue,
                expectedShortfall = result.expectedShortfall,
                steps = steps,
            )
            saveRunSafely(run)

            return result
        } catch (e: Exception) {
            runError = e.message ?: e.javaClass.simpleName
            val runCompletedAt = Instant.now()
            val run = CalculationRun(
                runId = runId,
                portfolioId = request.portfolioId.value,
                triggerType = triggerType,
                status = RunStatus.FAILED,
                startedAt = runStartedAt,
                completedAt = runCompletedAt,
                durationMs = java.time.Duration.between(runStartedAt, runCompletedAt).toMillis(),
                calculationType = request.calculationType.name,
                confidenceLevel = request.confidenceLevel.name,
                steps = steps,
                error = runError,
            )
            saveRunSafely(run)
            throw e
        }
    }

    private suspend fun saveRunSafely(run: CalculationRun) {
        try {
            runRecorder.save(run)
        } catch (e: Exception) {
            logger.warn("Failed to record calculation run {}", run.runId, e)
        }
    }
}
