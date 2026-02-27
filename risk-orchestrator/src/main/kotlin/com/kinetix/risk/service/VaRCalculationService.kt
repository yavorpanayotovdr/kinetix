package com.kinetix.risk.service

import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.kafka.RiskResultPublisher
import com.kinetix.risk.model.*
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class VaRCalculationService(
    private val positionProvider: PositionProvider,
    private val riskEngineClient: RiskEngineClient,
    private val resultPublisher: RiskResultPublisher,
    private val meterRegistry: MeterRegistry = SimpleMeterRegistry(),
    private val dependenciesDiscoverer: DependenciesDiscoverer? = null,
    private val marketDataFetcher: MarketDataFetcher? = null,
    private val jobRecorder: ValuationJobRecorder = NoOpValuationJobRecorder(),
    private val positionDependencyGrouper: PositionDependencyGrouper = PositionDependencyGrouper(),
) {
    private val logger = LoggerFactory.getLogger(VaRCalculationService::class.java)

    suspend fun calculateVaR(
        request: VaRCalculationRequest,
        triggerType: TriggerType = TriggerType.ON_DEMAND,
    ): ValuationResult? {
        val jobId = UUID.randomUUID()
        val jobStartedAt = Instant.now()
        val steps = mutableListOf<JobStep>()
        var jobError: String? = null

        saveJobSafely(
            ValuationJob(
                jobId = jobId,
                portfolioId = request.portfolioId.value,
                triggerType = triggerType,
                status = RunStatus.RUNNING,
                startedAt = jobStartedAt,
                calculationType = request.calculationType.name,
                confidenceLevel = request.confidenceLevel.name,
            )
        )

        try {
            // Step 1: Fetch positions
            val fetchPosStart = Instant.now()
            val positions = positionProvider.getPositions(request.portfolioId)
            val fetchPosDuration = java.time.Duration.between(fetchPosStart, Instant.now()).toMillis()
            steps.add(
                JobStep(
                    name = JobStepName.FETCH_POSITIONS,
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
                JobStep(
                    name = JobStepName.DISCOVER_DEPENDENCIES,
                    status = RunStatus.COMPLETED,
                    startedAt = discoverStart,
                    completedAt = Instant.now(),
                    durationMs = discoverDuration,
                    details = mapOf(
                        "dependencyCount" to dependencies.size,
                        "dataTypes" to dataTypes,
                        "dependencies" to Json.encodeToString(dependencies.map { dep ->
                            buildMap {
                                put("instrumentId", dep.instrumentId)
                                put("dataType", dep.dataType)
                                put("assetClass", dep.assetClass)
                                if (dep.parameters.isNotEmpty()) {
                                    put("parameters", dep.parameters.entries.joinToString(", ") { "${it.key}=${it.value}" })
                                }
                            }
                        }),
                    ),
                )
            )

            if (dependencies.isNotEmpty()) {
                val grouped = positionDependencyGrouper.group(positions, dependencies)
                if (grouped.isNotEmpty()) {
                    val groupedJson = Json.encodeToString(grouped.mapValues { (_, deps) ->
                        deps.map { dep ->
                            buildMap {
                                put("instrumentId", dep.instrumentId)
                                put("dataType", dep.dataType)
                                put("assetClass", dep.assetClass)
                                if (dep.parameters.isNotEmpty()) {
                                    put("parameters", dep.parameters.entries.joinToString(", ") { "${it.key}=${it.value}" })
                                }
                            }
                        }
                    })
                    steps[0] = steps[0].copy(details = steps[0].details + ("dependenciesByPosition" to groupedJson))
                }
            }

            // Step 3: Fetch market data
            val fetchMdStart = Instant.now()
            val fetchResults = try {
                if (dependencies.isNotEmpty() && marketDataFetcher != null) {
                    marketDataFetcher.fetch(dependencies)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                logger.warn("Market data fetch failed, proceeding with defaults", e)
                emptyList<FetchResult>()
            }
            val marketData = fetchResults.filterIsInstance<FetchSuccess>().map { it.value }
            val fetchMdDuration = java.time.Duration.between(fetchMdStart, Instant.now()).toMillis()
            steps.add(
                JobStep(
                    name = JobStepName.FETCH_MARKET_DATA,
                    status = RunStatus.COMPLETED,
                    startedAt = fetchMdStart,
                    completedAt = Instant.now(),
                    durationMs = fetchMdDuration,
                    details = mapOf(
                        "requested" to dependencies.size,
                        "fetched" to marketData.size,
                        "marketDataItems" to buildJsonArray {
                            dependencies.forEach { dep ->
                                val result = fetchResults.find { it.dependency == dep }
                                val fetched = (result as? FetchSuccess)?.value
                                val failure = result as? FetchFailure
                                addJsonObject {
                                    put("instrumentId", dep.instrumentId)
                                    put("dataType", dep.dataType)
                                    put("assetClass", dep.assetClass)
                                    put("status", if (fetched != null) "FETCHED" else "MISSING")
                                    if (fetched is ScalarMarketData) put("value", fetched.value.toString())
                                    if (fetched is CurveMarketData) put("points", fetched.points.size.toString())
                                    if (fetched is TimeSeriesMarketData) put("points", fetched.points.size.toString())
                                    if (fetched is MatrixMarketData) put("rows", fetched.rows.size.toString())
                                    if (failure != null) {
                                        putJsonObject("issue") {
                                            put("reason", failure.reason)
                                            failure.url?.let { put("url", it) }
                                            failure.httpStatus?.let { put("httpStatus", it.toString()) }
                                            failure.errorMessage?.let { put("errorMessage", it) }
                                            put("service", failure.service)
                                            put("timestamp", failure.timestamp.toString())
                                            put("durationMs", failure.durationMs.toString())
                                        }
                                    }
                                }
                            }
                        }.toString(),
                    ),
                )
            )

            // Step 4: Calculate VaR (+ Greeks if requested)
            val calcStart = Instant.now()
            val timer = meterRegistry.timer("var.calculation.duration")
            val sample = io.micrometer.core.instrument.Timer.start(meterRegistry)

            val result = riskEngineClient.valuate(request, positions, marketData)

            sample.stop(timer)
            meterRegistry.counter(
                "var.calculation.count",
                "calculationType", request.calculationType.name,
            ).increment()

            val calcDuration = java.time.Duration.between(calcStart, Instant.now()).toMillis()

            val positionBreakdown = computePositionBreakdown(positions, result)

            val calcDetails = buildMap<String, Any> {
                put("varValue", result.varValue ?: 0.0)
                put("expectedShortfall", result.expectedShortfall ?: 0.0)
                put("positionBreakdown", positionBreakdown)
                result.greeks?.let { greeks ->
                    put("greeksAssetClassCount", greeks.assetClassGreeks.size)
                    put("theta", greeks.theta)
                    put("rho", greeks.rho)
                }
                result.pvValue?.let { put("pvValue", it) }
            }

            steps.add(
                JobStep(
                    name = JobStepName.CALCULATE_VAR,
                    status = RunStatus.COMPLETED,
                    startedAt = calcStart,
                    completedAt = Instant.now(),
                    durationMs = calcDuration,
                    details = calcDetails,
                )
            )

            // Step 5: Publish result
            val publishStart = Instant.now()
            resultPublisher.publish(result)
            val publishDuration = java.time.Duration.between(publishStart, Instant.now()).toMillis()
            steps.add(
                JobStep(
                    name = JobStepName.PUBLISH_RESULT,
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

            val jobCompletedAt = Instant.now()
            val job = ValuationJob(
                jobId = jobId,
                portfolioId = request.portfolioId.value,
                triggerType = triggerType,
                status = RunStatus.COMPLETED,
                startedAt = jobStartedAt,
                completedAt = jobCompletedAt,
                durationMs = java.time.Duration.between(jobStartedAt, jobCompletedAt).toMillis(),
                calculationType = request.calculationType.name,
                confidenceLevel = request.confidenceLevel.name,
                varValue = result.varValue,
                expectedShortfall = result.expectedShortfall,
                steps = steps,
            )
            updateJobSafely(job)

            return result
        } catch (e: Exception) {
            jobError = e.message ?: e.javaClass.simpleName
            val jobCompletedAt = Instant.now()
            val job = ValuationJob(
                jobId = jobId,
                portfolioId = request.portfolioId.value,
                triggerType = triggerType,
                status = RunStatus.FAILED,
                startedAt = jobStartedAt,
                completedAt = jobCompletedAt,
                durationMs = java.time.Duration.between(jobStartedAt, jobCompletedAt).toMillis(),
                calculationType = request.calculationType.name,
                confidenceLevel = request.confidenceLevel.name,
                steps = steps,
                error = jobError,
            )
            updateJobSafely(job)
            throw e
        }
    }

    private fun computePositionBreakdown(positions: List<com.kinetix.common.model.Position>, result: ValuationResult): String {
        val breakdownByAssetClass = result.componentBreakdown.associateBy { it.assetClass }
        val greeksByAssetClass = result.greeks?.assetClassGreeks?.associateBy { it.assetClass }
        val marketValueByAssetClass = positions.groupBy { it.assetClass }
            .mapValues { (_, poses) -> poses.fold(BigDecimal.ZERO) { acc, p -> acc + p.marketValue.amount } }

        val varVal = result.varValue ?: 0.0
        val esVal = result.expectedShortfall ?: 0.0

        val items = positions.map { pos ->
            val breakdown = breakdownByAssetClass[pos.assetClass]
            val assetClassTotal = marketValueByAssetClass[pos.assetClass] ?: BigDecimal.ONE
            val weight = if (assetClassTotal.compareTo(BigDecimal.ZERO) != 0) {
                pos.marketValue.amount.divide(assetClassTotal, 10, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
            val varContribution = BigDecimal(breakdown?.varContribution ?: 0.0) * weight
            val varValue = BigDecimal(varVal)
            val percentageOfTotal = if (varValue.compareTo(BigDecimal.ZERO) != 0) {
                varContribution.divide(varValue, 10, RoundingMode.HALF_UP) * BigDecimal(100)
            } else {
                BigDecimal.ZERO
            }
            val esContribution = if (varValue.compareTo(BigDecimal.ZERO) != 0) {
                varContribution.divide(varValue, 10, RoundingMode.HALF_UP) * BigDecimal(esVal)
            } else {
                BigDecimal.ZERO
            }

            buildMap {
                put("instrumentId", pos.instrumentId.value)
                put("assetClass", pos.assetClass.name)
                put("marketValue", pos.marketValue.amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                put("varContribution", varContribution.setScale(2, RoundingMode.HALF_UP).toPlainString())
                put("esContribution", esContribution.setScale(2, RoundingMode.HALF_UP).toPlainString())
                put("percentageOfTotal", percentageOfTotal.setScale(2, RoundingMode.HALF_UP).toPlainString())
                greeksByAssetClass?.get(pos.assetClass)?.let { greeks ->
                    put("delta", "%.6f".format(greeks.delta))
                    put("gamma", "%.6f".format(greeks.gamma))
                    put("vega", "%.6f".format(greeks.vega))
                }
            }
        }
        return Json.encodeToString(items)
    }

    private suspend fun saveJobSafely(job: ValuationJob) {
        try {
            jobRecorder.save(job)
        } catch (e: Exception) {
            logger.warn("Failed to record valuation job {}", job.jobId, e)
        }
    }

    private suspend fun updateJobSafely(job: ValuationJob) {
        try {
            jobRecorder.update(job)
        } catch (e: Exception) {
            logger.warn("Failed to update valuation job {}", job.jobId, e)
        }
    }
}
