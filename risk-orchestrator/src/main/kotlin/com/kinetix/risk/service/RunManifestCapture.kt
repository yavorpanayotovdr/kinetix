package com.kinetix.risk.service

import com.kinetix.common.model.Position
import com.kinetix.risk.model.ComponentBreakdown
import com.kinetix.risk.model.FetchResult
import com.kinetix.risk.model.RunManifest
import com.kinetix.risk.model.VaRCalculationRequest
import java.time.LocalDate
import java.util.UUID

interface RunManifestCapture {
    suspend fun captureInputs(
        jobId: UUID,
        request: VaRCalculationRequest,
        positions: List<Position>,
        fetchResults: List<FetchResult>,
        valuationDate: LocalDate,
    ): RunManifest

    suspend fun finaliseOutputs(
        manifestId: UUID,
        modelVersion: String,
        varValue: Double?,
        expectedShortfall: Double?,
        componentBreakdown: List<ComponentBreakdown>,
    )
}
