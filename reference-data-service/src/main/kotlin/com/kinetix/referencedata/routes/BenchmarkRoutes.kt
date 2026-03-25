package com.kinetix.referencedata.routes

import com.kinetix.referencedata.model.Benchmark
import com.kinetix.referencedata.model.BenchmarkConstituent
import com.kinetix.referencedata.model.BenchmarkDailyReturn
import com.kinetix.referencedata.routes.dtos.BenchmarkConstituentResponse
import com.kinetix.referencedata.routes.dtos.BenchmarkDetailResponse
import com.kinetix.referencedata.routes.dtos.BenchmarkResponse
import com.kinetix.referencedata.routes.dtos.CreateBenchmarkRequest
import com.kinetix.referencedata.routes.dtos.RecordBenchmarkReturnRequest
import com.kinetix.referencedata.routes.dtos.UpdateBenchmarkConstituentsRequest
import com.kinetix.referencedata.service.BenchmarkService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

fun Route.benchmarkRoutes(benchmarkService: BenchmarkService) {
    route("/api/v1/benchmarks") {
        get({
            summary = "List all benchmarks"
            tags = listOf("Benchmarks")
        }) {
            val benchmarks = benchmarkService.findAll()
            call.respond(benchmarks.map { it.toResponse() })
        }

        post({
            summary = "Create a benchmark"
            tags = listOf("Benchmarks")
            request {
                body<CreateBenchmarkRequest>()
            }
        }) {
            val request = call.receive<CreateBenchmarkRequest>()
            val benchmark = Benchmark(
                benchmarkId = request.benchmarkId,
                name = request.name,
                description = request.description,
                createdAt = Instant.now(),
            )
            benchmarkService.create(benchmark)
            call.respond(HttpStatusCode.Created, benchmark.toResponse())
        }

        route("/{id}") {
            get({
                summary = "Get benchmark with current constituents"
                tags = listOf("Benchmarks")
                request {
                    pathParameter<String>("id") { description = "Benchmark identifier" }
                    queryParameter<String>("asOfDate") {
                        description = "Constituent snapshot date (YYYY-MM-DD). Defaults to today."
                        required = false
                    }
                }
            }) {
                val id = call.requirePathParam("id")
                val asOfDateParam = call.request.queryParameters["asOfDate"]
                val asOfDate = if (asOfDateParam != null) {
                    try {
                        LocalDate.parse(asOfDateParam)
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid 'asOfDate' format. Expected YYYY-MM-DD.")
                        return@get
                    }
                } else {
                    LocalDate.now()
                }

                val benchmark = benchmarkService.findById(id)
                if (benchmark == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val constituents = benchmarkService.findConstituents(id, asOfDate)
                call.respond(benchmark.toDetailResponse(constituents, asOfDate))
            }

            put("/constituents", {
                summary = "Replace benchmark constituent weights"
                tags = listOf("Benchmarks")
                request {
                    pathParameter<String>("id") { description = "Benchmark identifier" }
                    body<UpdateBenchmarkConstituentsRequest>()
                }
            }) {
                val id = call.requirePathParam("id")
                val benchmark = benchmarkService.findById(id)
                if (benchmark == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@put
                }

                val request = call.receive<UpdateBenchmarkConstituentsRequest>()
                val asOfDate = try {
                    LocalDate.parse(request.asOfDate)
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid 'asOfDate' format. Expected YYYY-MM-DD.")
                    return@put
                }

                val constituents = request.constituents.map { dto ->
                    BenchmarkConstituent(
                        benchmarkId = id,
                        instrumentId = dto.instrumentId,
                        weight = BigDecimal(dto.weight),
                        asOfDate = asOfDate,
                    )
                }
                benchmarkService.replaceConstituents(id, constituents)
                call.respond(HttpStatusCode.NoContent, "")
            }

            post("/returns", {
                summary = "Record a benchmark daily return"
                tags = listOf("Benchmarks")
                request {
                    pathParameter<String>("id") { description = "Benchmark identifier" }
                    body<RecordBenchmarkReturnRequest>()
                }
            }) {
                val id = call.requirePathParam("id")
                val benchmark = benchmarkService.findById(id)
                if (benchmark == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }

                val request = call.receive<RecordBenchmarkReturnRequest>()
                val returnDate = try {
                    LocalDate.parse(request.returnDate)
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid 'returnDate' format. Expected YYYY-MM-DD.")
                    return@post
                }

                val benchmarkReturn = BenchmarkDailyReturn(
                    benchmarkId = id,
                    returnDate = returnDate,
                    dailyReturn = BigDecimal(request.dailyReturn),
                )
                benchmarkService.recordReturn(benchmarkReturn)
                call.respond(HttpStatusCode.Created, "")
            }
        }
    }
}

private fun Benchmark.toResponse() = BenchmarkResponse(
    benchmarkId = benchmarkId,
    name = name,
    description = description,
    createdAt = createdAt.toString(),
)

private fun Benchmark.toDetailResponse(
    constituents: List<BenchmarkConstituent>,
    asOfDate: LocalDate,
) = BenchmarkDetailResponse(
    benchmarkId = benchmarkId,
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    constituents = constituents.map { c ->
        BenchmarkConstituentResponse(
            instrumentId = c.instrumentId,
            weight = c.weight.toPlainString(),
            asOfDate = c.asOfDate.toString(),
        )
    },
)
