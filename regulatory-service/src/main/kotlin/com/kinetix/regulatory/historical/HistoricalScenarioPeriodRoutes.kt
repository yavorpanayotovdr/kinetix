package com.kinetix.regulatory.historical

import com.kinetix.regulatory.historical.dto.HistoricalScenarioPeriodResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.historicalScenarioPeriodRoutes(repository: HistoricalScenarioRepository) {
    route("/api/v1/historical-periods") {
        get({
            summary = "List all historical scenario periods"
            tags = listOf("Historical Scenarios")
        }) {
            val periods = repository.findAllPeriods()
            call.respond(periods.map { it.toResponse() })
        }

        get("/{periodId}", {
            summary = "Get a historical scenario period by ID"
            tags = listOf("Historical Scenarios")
            request {
                pathParameter<String>("periodId") { description = "Period identifier" }
            }
        }) {
            val periodId = call.parameters["periodId"]
                ?: throw IllegalArgumentException("Missing required path parameter: periodId")
            val period = repository.findPeriodById(periodId)
            if (period == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(period.toResponse())
            }
        }
    }
}

private fun HistoricalScenarioPeriod.toResponse() = HistoricalScenarioPeriodResponse(
    periodId = periodId,
    name = name,
    description = description,
    startDate = startDate,
    endDate = endDate,
    assetClassFocus = assetClassFocus,
    severityLabel = severityLabel,
)
