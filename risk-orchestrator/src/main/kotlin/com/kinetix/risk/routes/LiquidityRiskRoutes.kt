package com.kinetix.risk.routes

import com.kinetix.common.model.BookId
import com.kinetix.common.model.LiquidityRiskResult
import com.kinetix.risk.persistence.LiquidityRiskSnapshotRepository
import com.kinetix.risk.routes.dtos.LiquidityRiskResponse
import com.kinetix.risk.routes.dtos.PositionLiquidityRiskDto
import com.kinetix.risk.service.LiquidityRiskService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
private data class LiquidityRiskCalculationRequest(val baseVar: Double)

fun Route.liquidityRiskRoutes(
    liquidityRiskService: LiquidityRiskService,
    snapshotRepository: LiquidityRiskSnapshotRepository,
) {
    post("/api/v1/books/{bookId}/liquidity-risk") {
        val bookId = BookId(call.requirePathParam("bookId"))
        val request = call.receive<LiquidityRiskCalculationRequest>()

        val result = liquidityRiskService.calculateAndSave(bookId, request.baseVar)

        if (result == null) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            call.respond(result.toResponse())
        }
    }

    get("/api/v1/books/{bookId}/liquidity-risk/latest") {
        val bookId = call.requirePathParam("bookId")
        val result = snapshotRepository.findLatestByBookId(bookId)

        if (result == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(result.toResponse())
        }
    }

    get("/api/v1/books/{bookId}/liquidity-risk") {
        val bookId = call.requirePathParam("bookId")
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
        val results = snapshotRepository.findAllByBookId(bookId, limit)
        call.respond(results.map { it.toResponse() })
    }
}

private fun LiquidityRiskResult.toResponse() = LiquidityRiskResponse(
    bookId = bookId,
    portfolioLvar = portfolioLvar,
    dataCompleteness = dataCompleteness,
    portfolioConcentrationStatus = portfolioConcentrationStatus,
    calculatedAt = calculatedAt,
    positionRisks = positionRisks.map { pos ->
        PositionLiquidityRiskDto(
            instrumentId = pos.instrumentId,
            assetClass = pos.assetClass,
            marketValue = pos.marketValue,
            tier = pos.tier.name,
            horizonDays = pos.horizonDays,
            adv = pos.adv,
            advMissing = pos.advMissing,
            advStale = pos.advStale,
            lvarContribution = pos.lvarContribution,
            stressedLiquidationValue = pos.stressedLiquidationValue,
            concentrationStatus = pos.concentrationStatus,
        )
    },
)
