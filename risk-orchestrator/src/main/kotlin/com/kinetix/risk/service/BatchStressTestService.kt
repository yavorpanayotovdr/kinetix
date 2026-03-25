package com.kinetix.risk.service

import com.kinetix.common.model.BookId
import com.kinetix.proto.common.BookId as ProtoBookId
import com.kinetix.proto.risk.StressTestRequest
import com.kinetix.proto.risk.StressTestServiceGrpcKt.StressTestServiceCoroutineStub
import com.kinetix.risk.client.PositionProvider
import com.kinetix.risk.mapper.toProto
import com.kinetix.risk.model.BatchScenarioFailure
import com.kinetix.risk.model.BatchScenarioResult
import com.kinetix.risk.model.BatchStressRunResult
import com.kinetix.risk.model.CalculationType
import com.kinetix.risk.model.ConfidenceLevel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Runs all approved stress scenarios against a book in parallel.
 *
 * The service fetches positions once and shares them across all scenario calls.
 * Base VaR is taken from the first scenario response (the risk engine returns it
 * as part of every StressTestResponse, so all calls will return the same value
 * given identical positions). Individual scenario failures are captured and reported
 * separately — a single failing scenario never aborts the batch.
 *
 * Results are returned ranked from worst to best P&L impact.
 */
class BatchStressTestService(
    private val stressTestStub: StressTestServiceCoroutineStub,
    private val positionProvider: PositionProvider,
) {
    private val log = LoggerFactory.getLogger(BatchStressTestService::class.java)

    suspend fun runBatch(
        bookId: BookId,
        scenarioNames: List<String>,
        calculationType: String = "PARAMETRIC",
        confidenceLevel: String = "CL_95",
        timeHorizonDays: Int = 1,
    ): BatchStressRunResult {
        if (scenarioNames.isEmpty()) {
            return BatchStressRunResult(
                results = emptyList(),
                failedScenarios = emptyList(),
                worstScenarioName = null,
                worstPnlImpact = null,
            )
        }

        val positions = positionProvider.getPositions(bookId)
        val protoPositions = positions.map { it.toProto() }
        val protoBookId = ProtoBookId.newBuilder().setValue(bookId.value).build()
        val calcType = CalculationType.valueOf(calculationType).toProto()
        val confLevel = ConfidenceLevel.valueOf(confidenceLevel).toProto()

        val outcomesByName: List<Pair<String, Result<BatchScenarioResult>>> = coroutineScope {
            scenarioNames.map { scenarioName ->
                scenarioName to async {
                    runCatching {
                        val request = StressTestRequest.newBuilder()
                            .setBookId(protoBookId)
                            .setScenarioName(scenarioName)
                            .setCalculationType(calcType)
                            .setConfidenceLevel(confLevel)
                            .setTimeHorizonDays(timeHorizonDays)
                            .addAllPositions(protoPositions)
                            .build()
                        val response = stressTestStub.runStressTest(request)
                        BatchScenarioResult(
                            scenarioName = response.scenarioName,
                            baseVar = "%.2f".format(response.baseVar),
                            stressedVar = "%.2f".format(response.stressedVar),
                            pnlImpact = "%.2f".format(response.pnlImpact),
                        )
                    }
                }
            }.map { (name, deferred) -> name to deferred.await() }
        }

        val successes = mutableListOf<BatchScenarioResult>()
        val failures = mutableListOf<BatchScenarioFailure>()

        for ((scenarioName, outcome) in outcomesByName) {
            outcome.fold(
                onSuccess = { successes.add(it) },
                onFailure = { ex ->
                    log.warn("Stress scenario {} failed: {}", scenarioName, ex.message)
                    failures.add(
                        BatchScenarioFailure(
                            scenarioName = scenarioName,
                            errorMessage = ex.message ?: "Unknown error",
                        )
                    )
                },
            )
        }

        // Rank from worst (most negative) to best P&L impact
        val ranked = successes.sortedBy { it.pnlImpact.toDoubleOrNull() ?: 0.0 }

        val worst = ranked.firstOrNull()
        return BatchStressRunResult(
            results = ranked,
            failedScenarios = failures,
            worstScenarioName = worst?.scenarioName,
            worstPnlImpact = worst?.pnlImpact,
        )
    }
}
