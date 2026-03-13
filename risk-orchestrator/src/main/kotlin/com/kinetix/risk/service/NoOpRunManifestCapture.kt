package com.kinetix.risk.service

import com.kinetix.common.model.Position
import com.kinetix.risk.model.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class NoOpRunManifestCapture : RunManifestCapture {
    override suspend fun captureInputs(
        jobId: UUID,
        request: VaRCalculationRequest,
        positions: List<Position>,
        fetchResults: List<FetchResult>,
        valuationDate: LocalDate,
    ): RunManifest = RunManifest(
        manifestId = UUID.randomUUID(),
        jobId = jobId,
        portfolioId = request.portfolioId.value,
        valuationDate = valuationDate,
        capturedAt = Instant.now(),
        modelVersion = "",
        calculationType = request.calculationType.name,
        confidenceLevel = request.confidenceLevel.name,
        timeHorizonDays = request.timeHorizonDays,
        numSimulations = request.numSimulations,
        monteCarloSeed = request.monteCarloSeed,
        positionCount = positions.size,
        positionDigest = "",
        marketDataDigest = "",
        inputDigest = "",
        status = ManifestStatus.INPUTS_FROZEN,
    )

    override suspend fun finaliseOutputs(
        manifestId: UUID,
        modelVersion: String,
        varValue: Double?,
        expectedShortfall: Double?,
        componentBreakdown: List<ComponentBreakdown>,
    ) {
        // no-op
    }
}
