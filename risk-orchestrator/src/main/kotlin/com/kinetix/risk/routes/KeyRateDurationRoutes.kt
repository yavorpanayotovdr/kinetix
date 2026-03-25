package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.risk.model.InstrumentKrdResult
import com.kinetix.risk.model.KrdBucket
import com.kinetix.risk.routes.dtos.InstrumentKrdResultDto
import com.kinetix.risk.routes.dtos.KrdBucketDto
import com.kinetix.risk.routes.dtos.KeyRateDurationResponse
import com.kinetix.risk.service.KeyRateDurationService
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal

fun Route.keyRateDurationRoutes(service: KeyRateDurationService) {
    get("/api/v1/risk/krd/{bookId}", {
        summary = "Key rate durations for all fixed-income positions in a book"
        tags = listOf("Key Rate Duration")
        request {
            pathParameter<String>("bookId") { description = "Book identifier" }
        }
        response {
            code(HttpStatusCode.OK) { body<KeyRateDurationResponse>() }
        }
    }) {
        val bookId = BookId(call.requirePathParam("bookId"))
        val results = service.calculate(bookId)

        val instrumentDtos = results.map { it.toDto() }
        val aggregated = aggregateByTenor(results)

        call.respond(
            KeyRateDurationResponse(
                bookId = bookId.value,
                instruments = instrumentDtos,
                aggregated = aggregated,
            )
        )
    }
}

private fun InstrumentKrdResult.toDto() = InstrumentKrdResultDto(
    instrumentId = instrumentId,
    krdBuckets = krdBuckets.map { it.toDto() },
    totalDv01 = totalDv01.toPlainString(),
)

private fun KrdBucket.toDto() = KrdBucketDto(
    tenorLabel = tenorLabel,
    tenorDays = tenorDays,
    dv01 = dv01.toPlainString(),
)

private fun aggregateByTenor(results: List<InstrumentKrdResult>): List<KrdBucketDto> {
    if (results.isEmpty()) return emptyList()

    val bucketOrder = results.first().krdBuckets.map { it.tenorLabel to it.tenorDays }
    val totals = mutableMapOf<String, BigDecimal>()
    for (result in results) {
        for (bucket in result.krdBuckets) {
            totals[bucket.tenorLabel] = (totals[bucket.tenorLabel] ?: BigDecimal.ZERO) + bucket.dv01
        }
    }

    return bucketOrder.map { (label, days) ->
        KrdBucketDto(
            tenorLabel = label,
            tenorDays = days,
            dv01 = (totals[label] ?: BigDecimal.ZERO).toPlainString(),
        )
    }
}
