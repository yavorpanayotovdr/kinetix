package com.kinetix.risk.routes

import com.kinetix.risk.model.RegimeState
import com.kinetix.risk.persistence.MarketRegimeRepository
import com.kinetix.risk.routes.dtos.AdaptiveVaRParametersDto
import com.kinetix.risk.routes.dtos.MarketRegimeCurrentResponse
import com.kinetix.risk.routes.dtos.MarketRegimeHistoryItemResponse
import com.kinetix.risk.routes.dtos.MarketRegimeHistoryResponse
import com.kinetix.risk.routes.dtos.RegimeSignalsDto
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.marketRegimeRoutes(
    currentStateProvider: () -> RegimeState,
    repository: MarketRegimeRepository,
) {
    get("/api/v1/risk/regime/current", {
        summary = "Get the current market regime and adaptive VaR parameters"
        tags = listOf("Market Regime")
        response {
            code(HttpStatusCode.OK) { body<MarketRegimeCurrentResponse>() }
        }
    }) {
        val state = currentStateProvider()
        call.respond(state.toCurrentResponse())
    }

    get("/api/v1/risk/regime/history", {
        summary = "Get market regime history in descending order"
        tags = listOf("Market Regime")
        request {
            queryParameter<Int>("limit") {
                description = "Maximum number of records to return (default 50)"
                required = false
            }
        }
        response {
            code(HttpStatusCode.OK) { body<MarketRegimeHistoryResponse>() }
        }
    }) {
        val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
        val items = repository.findRecent(limit)
        call.respond(
            MarketRegimeHistoryResponse(
                items = items.map { it.toHistoryItemResponse() },
                total = items.size,
            )
        )
    }
}

private fun RegimeState.toCurrentResponse() = MarketRegimeCurrentResponse(
    regime = regime.name,
    isConfirmed = isConfirmed,
    confidence = confidence,
    consecutiveObservations = consecutiveObservations,
    detectedAt = detectedAt.toString(),
    degradedInputs = degradedInputs,
    signals = RegimeSignalsDto(
        realisedVol20d = signals.realisedVol20d,
        crossAssetCorrelation = signals.crossAssetCorrelation,
        creditSpreadBps = signals.creditSpreadBps,
        pnlVolatility = signals.pnlVolatility,
    ),
    varParameters = AdaptiveVaRParametersDto(
        calculationType = varParameters.calculationType.name,
        confidenceLevel = varParameters.confidenceLevel.name,
        timeHorizonDays = varParameters.timeHorizonDays,
        correlationMethod = varParameters.correlationMethod,
        numSimulations = varParameters.numSimulations,
    ),
)

private fun com.kinetix.risk.model.MarketRegimeHistory.toHistoryItemResponse() = MarketRegimeHistoryItemResponse(
    id = id.toString(),
    regime = regime.name,
    startedAt = startedAt.toString(),
    endedAt = endedAt?.toString(),
    durationMs = durationMs,
    confidence = confidence,
    consecutiveObservations = consecutiveObservations,
    degradedInputs = degradedInputs,
    signals = RegimeSignalsDto(
        realisedVol20d = signals.realisedVol20d,
        crossAssetCorrelation = signals.crossAssetCorrelation,
        creditSpreadBps = signals.creditSpreadBps,
        pnlVolatility = signals.pnlVolatility,
    ),
    varParameters = AdaptiveVaRParametersDto(
        calculationType = varParameters.calculationType.name,
        confidenceLevel = varParameters.confidenceLevel.name,
        timeHorizonDays = varParameters.timeHorizonDays,
        correlationMethod = varParameters.correlationMethod,
        numSimulations = varParameters.numSimulations,
    ),
)
