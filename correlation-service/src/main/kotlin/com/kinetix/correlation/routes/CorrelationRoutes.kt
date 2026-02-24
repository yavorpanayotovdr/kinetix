package com.kinetix.correlation.routes

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.correlation.persistence.CorrelationMatrixRepository
import com.kinetix.correlation.routes.dtos.CorrelationMatrixResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.correlationRoutes(
    correlationMatrixRepository: CorrelationMatrixRepository,
) {
    route("/api/v1/correlations") {
        get("/latest") {
            val labels = call.requireQueryParam("labels").split(",")
            val windowDays = call.requireQueryParam("window").toInt()
            val matrix = correlationMatrixRepository.findLatest(labels, windowDays)
            if (matrix != null) {
                call.respond(matrix.toResponse())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

private fun CorrelationMatrix.toResponse() = CorrelationMatrixResponse(
    labels = labels,
    values = values,
    windowDays = windowDays,
    asOfDate = asOfDate.toString(),
    method = method.name,
)
