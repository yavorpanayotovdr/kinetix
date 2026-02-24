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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
            get("/latest") {
                val curveId = call.requirePathParam("curveId")
                val curve = yieldCurveRepository.findLatest(curveId)
                if (curve != null) {
                    call.respond(curve.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/history") {
                val curveId = call.requirePathParam("curveId")
                val from = call.queryParameters["from"]
                    ?: throw IllegalArgumentException("Missing required query parameter: from")
                val to = call.queryParameters["to"]
                    ?: throw IllegalArgumentException("Missing required query parameter: to")
                val curves = yieldCurveRepository.findByTimeRange(curveId, Instant.parse(from), Instant.parse(to))
                call.respond(curves.map { it.toResponse() })
            }
        }

        post("/yield-curves") {
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

        route("/risk-free/{currency}") {
            get("/latest") {
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

        post("/risk-free") {
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

        route("/forwards/{instrumentId}") {
            get("/latest") {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val curve = forwardCurveRepository.findLatest(instrumentId)
                if (curve != null) {
                    call.respond(curve.toResponse())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/history") {
                val instrumentId = InstrumentId(call.requirePathParam("instrumentId"))
                val from = call.queryParameters["from"]
                    ?: throw IllegalArgumentException("Missing required query parameter: from")
                val to = call.queryParameters["to"]
                    ?: throw IllegalArgumentException("Missing required query parameter: to")
                val curves = forwardCurveRepository.findByTimeRange(instrumentId, Instant.parse(from), Instant.parse(to))
                call.respond(curves.map { it.toResponse() })
            }
        }

        post("/forwards") {
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
