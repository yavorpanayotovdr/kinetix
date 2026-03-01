package com.kinetix.rates.routes

import com.kinetix.common.model.CurvePoint
import com.kinetix.common.model.ForwardCurve
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.RateSource
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.Tenor
import com.kinetix.common.model.YieldCurve
import com.kinetix.rates.persistence.ForwardCurveRepository
import com.kinetix.rates.persistence.RiskFreeRateRepository
import com.kinetix.rates.persistence.YieldCurveRepository
import com.kinetix.rates.routes.dtos.*
import com.kinetix.rates.service.RateIngestionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.routing.route
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

fun Route.ratesRoutes(
    yieldCurveRepository: YieldCurveRepository,
    riskFreeRateRepository: RiskFreeRateRepository,
    forwardCurveRepository: ForwardCurveRepository,
    ingestionService: RateIngestionService,
) {
    route("/api/v1/rates") {
        route("/yield-curves/{curveId}") {
            route("/latest") {
                get({
                    summary = "Get latest yield curve"
                    tags = listOf("Yield Curves")
                    request {
                        pathParameter<String>("curveId") { description = "Yield curve identifier" }
                    }
                }) {
                    val curveId = call.requirePathParam("curveId")
                    val curve = yieldCurveRepository.findLatest(curveId)
                    if (curve != null) {
                        call.respond(curve.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            route("/history") {
                get({
                    summary = "Get yield curve history"
                    tags = listOf("Yield Curves")
                    request {
                        pathParameter<String>("curveId") { description = "Yield curve identifier" }
                        queryParameter<String>("from") { description = "Start of time range" }
                        queryParameter<String>("to") { description = "End of time range" }
                    }
                }) {
                    val curveId = call.requirePathParam("curveId")
                    val from = call.queryParameters["from"]
                        ?: throw IllegalArgumentException("Missing required query parameter: from")
                    val to = call.queryParameters["to"]
                        ?: throw IllegalArgumentException("Missing required query parameter: to")
                    val curves = yieldCurveRepository.findByTimeRange(curveId, Instant.parse(from), Instant.parse(to))
                    call.respond(curves.map { it.toResponse() })
                }
            }
        }

        route("/yield-curves") {
            post({
                summary = "Ingest a yield curve"
                tags = listOf("Yield Curves")
                request {
                    body<IngestYieldCurveRequest>()
                }
            }) {
                val request = call.receive<IngestYieldCurveRequest>()
                val curve = YieldCurve(
                    curveId = request.curveId,
                    currency = Currency.getInstance(request.currency),
                    tenors = request.tenors.map { Tenor(it.label, it.days, BigDecimal(it.rate)) },
                    asOf = Instant.now(),
                    source = RateSource.valueOf(request.source),
                )
                ingestionService.ingest(curve)
                call.respond(HttpStatusCode.Created, curve.toResponse())
            }
        }

        route("/risk-free/{currency}") {
            route("/latest") {
                get({
                    summary = "Get latest risk-free rate"
                    tags = listOf("Risk-Free Rates")
                    request {
                        pathParameter<String>("currency") { description = "Currency code" }
                        queryParameter<String>("tenor") { description = "Rate tenor" }
                    }
                }) {
                    val currencyCode = call.requirePathParam("currency")
                    val currency = Currency.getInstance(currencyCode)
                    val tenor = call.queryParameters["tenor"]
                        ?: throw IllegalArgumentException("Missing required query parameter: tenor")
                    val rate = riskFreeRateRepository.findLatest(currency, tenor)
                    if (rate != null) {
                        call.respond(rate.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }

        route("/risk-free") {
            post({
                summary = "Ingest a risk-free rate"
                tags = listOf("Risk-Free Rates")
                request {
                    body<IngestRiskFreeRateRequest>()
                }
            }) {
                val request = call.receive<IngestRiskFreeRateRequest>()
                val rate = RiskFreeRate(
                    currency = Currency.getInstance(request.currency),
                    tenor = request.tenor,
                    rate = BigDecimal(request.rate).toDouble(),
                    asOfDate = Instant.now(),
                    source = RateSource.valueOf(request.source),
                )
                ingestionService.ingest(rate)
                call.respond(HttpStatusCode.Created, rate.toResponse())
            }
        }

        route("/forwards/{instrumentId}") {
            route("/latest") {
                get({
                    summary = "Get latest forward curve"
                    tags = listOf("Forward Curves")
                    request {
                        pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                    }
                }) {
                    val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                    val curve = forwardCurveRepository.findLatest(instrumentId)
                    if (curve != null) {
                        call.respond(curve.toResponse())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            route("/history") {
                get({
                    summary = "Get forward curve history"
                    tags = listOf("Forward Curves")
                    request {
                        pathParameter<String>("instrumentId") { description = "Instrument identifier" }
                        queryParameter<String>("from") { description = "Start of time range" }
                        queryParameter<String>("to") { description = "End of time range" }
                    }
                }) {
                    val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                    val from = call.queryParameters["from"]
                        ?: throw IllegalArgumentException("Missing required query parameter: from")
                    val to = call.queryParameters["to"]
                        ?: throw IllegalArgumentException("Missing required query parameter: to")
                    val curves = forwardCurveRepository.findByTimeRange(instrumentId, Instant.parse(from), Instant.parse(to))
                    call.respond(curves.map { it.toResponse() })
                }
            }
        }

        route("/forwards") {
            post({
                summary = "Ingest a forward curve"
                tags = listOf("Forward Curves")
                request {
                    body<IngestForwardCurveRequest>()
                }
            }) {
                val request = call.receive<IngestForwardCurveRequest>()
                val curve = ForwardCurve(
                    instrumentId = InstrumentId(request.instrumentId),
                    assetClass = request.assetClass,
                    points = request.points.map { CurvePoint(it.tenor, BigDecimal(it.value).toDouble()) },
                    asOfDate = Instant.now(),
                    source = RateSource.valueOf(request.source),
                )
                ingestionService.ingest(curve)
                call.respond(HttpStatusCode.Created, curve.toResponse())
            }
        }
    }
}

private fun YieldCurve.toResponse() = YieldCurveResponse(
    curveId = curveId,
    currency = currency.currencyCode,
    tenors = tenors.map { TenorDto(it.label, it.days, it.rate.toPlainString()) },
    asOfDate = asOf.toString(),
    source = source.name,
)

private fun RiskFreeRate.toResponse() = RiskFreeRateResponse(
    currency = currency.currencyCode,
    tenor = tenor,
    rate = rate.toBigDecimal().toPlainString(),
    asOfDate = asOfDate.toString(),
    source = source.name,
)

private fun ForwardCurve.toResponse() = ForwardCurveResponse(
    instrumentId = instrumentId.value,
    assetClass = assetClass,
    points = points.map { CurvePointDto(it.tenor, it.value.toBigDecimal().toPlainString()) },
    asOfDate = asOfDate.toString(),
    source = source.name,
)
