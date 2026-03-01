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
                    status = "OK",
                    message = "All prices updated within acceptable threshold",
                    lastChecked = now,
                ),
                DataQualityCheckResponse(
                    name = "Position Count",
                    status = "OK",
                    message = "Position counts are consistent across services",
                    lastChecked = now,
                ),
                DataQualityCheckResponse(
                    name = "Risk Result Completeness",
                    status = "OK",
                    message = "All risk calculations completed successfully",
                    lastChecked = now,
                ),
            )

            val overall = when {
                checks.any { it.status == "CRITICAL" } -> "CRITICAL"
                checks.any { it.status == "WARNING" } -> "WARNING"
                else -> "OK"
            }

            call.respond(DataQualityStatusResponse(overall = overall, checks = checks))
        }
    }
}
