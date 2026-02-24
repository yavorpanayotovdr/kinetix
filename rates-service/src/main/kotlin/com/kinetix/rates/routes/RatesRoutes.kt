package com.kinetix.rates.routes

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.YieldCurve
import com.kinetix.common.model.RiskFreeRate
import com.kinetix.common.model.ForwardCurve
import com.kinetix.rates.persistence.YieldCurveRepository
import com.kinetix.rates.persistence.RiskFreeRateRepository
import com.kinetix.rates.persistence.ForwardCurveRepository
import com.kinetix.rates.routes.dtos.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.time.Instant
import java.util.Currency

fun Route.ratesRoutes(
    yieldCurveRepository: YieldCurveRepository,
    riskFreeRateRepository: RiskFreeRateRepository,
    forwardCurveRepository: ForwardCurveRepository,
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
