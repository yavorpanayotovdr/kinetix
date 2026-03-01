package com.kinetix.correlation.routes

import com.kinetix.common.model.CorrelationMatrix
import com.kinetix.common.model.EstimationMethod
import com.kinetix.correlation.persistence.CorrelationMatrixRepository
import com.kinetix.correlation.routes.dtos.CorrelationMatrixResponse
import com.kinetix.correlation.routes.dtos.IngestCorrelationMatrixRequest
import com.kinetix.correlation.service.CorrelationIngestionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.routing.route
import java.time.Instant

fun Route.correlationRoutes(
    correlationMatrixRepository: CorrelationMatrixRepository,
    ingestionService: CorrelationIngestionService,
) {
    route("/api/v1/correlations") {
        route("/latest") {
            get({
                summary = "Get latest correlation matrix"
                tags = listOf("Correlations")
                request {
                    queryParameter<String>("labels") { description = "Comma-separated instrument labels" }
                    queryParameter<Int>("window") { description = "Window size in days" }
                }
            }) {
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

        route("/ingest") {
            post({
                summary = "Ingest a correlation matrix"
                tags = listOf("Correlations")
                request {
                    body<IngestCorrelationMatrixRequest>()
                }
            }) {
                val request = call.receive<IngestCorrelationMatrixRequest>()
                val matrix = CorrelationMatrix(
                    labels = request.labels,
                    values = request.values,
                    windowDays = request.windowDays,
                    asOfDate = Instant.now(),
                    method = EstimationMethod.valueOf(request.method),
                )
                ingestionService.ingest(matrix)
                call.respond(HttpStatusCode.Created, matrix.toResponse())
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
