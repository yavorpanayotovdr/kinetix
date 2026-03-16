package com.kinetix.gateway.routes

import com.kinetix.gateway.dto.DataQualityCheckResponse
import com.kinetix.gateway.dto.DataQualityStatusResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.dataQualityRoutes() {
    route("/api/v1/data-quality") {
        get("/status", {
            summary = "Get data quality status"
            tags = listOf("Data Quality")
        }) {
            val now = Instant.now().toString()
            val checks = listOf(
                DataQualityCheckResponse(
                    name = "Price Freshness",
                    status = "UNKNOWN",
                    message = "not implemented",
                    lastChecked = now,
                ),
                DataQualityCheckResponse(
                    name = "Position Count",
                    status = "UNKNOWN",
                    message = "not implemented",
                    lastChecked = now,
                ),
                DataQualityCheckResponse(
                    name = "Risk Result Completeness",
                    status = "UNKNOWN",
                    message = "not implemented",
                    lastChecked = now,
                ),
            )

            val overall = when {
                checks.any { it.status == "CRITICAL" } -> "CRITICAL"
                checks.any { it.status == "WARNING" } -> "WARNING"
                checks.all { it.status == "UNKNOWN" } -> "UNKNOWN"
                else -> "OK"
            }

            call.respond(DataQualityStatusResponse(overall = overall, checks = checks))
        }
    }
}
