package com.kinetix.position.routes

import com.kinetix.position.fix.ExecutionCostAnalysis
import com.kinetix.position.fix.ExecutionCostRepository
import com.kinetix.position.fix.PrimeBrokerPosition
import com.kinetix.position.fix.PrimeBrokerReconciliation
import com.kinetix.position.fix.PrimeBrokerReconciliationRepository
import com.kinetix.position.fix.PrimeBrokerReconciliationService
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.routes.dtos.ExecutionCostResponse
import com.kinetix.position.routes.dtos.PrimeBrokerPositionDto
import com.kinetix.position.routes.dtos.PrimeBrokerStatementRequest
import com.kinetix.position.routes.dtos.ReconciliationBreakDto
import com.kinetix.position.routes.dtos.ReconciliationResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

fun Route.executionRoutes(
    executionCostRepository: ExecutionCostRepository,
    primeBrokerReconciliationRepository: PrimeBrokerReconciliationRepository,
    reconciliationService: PrimeBrokerReconciliationService,
    positionRepository: PositionRepository,
) {
    route("/api/v1/execution") {

        route("/cost/{bookId}") {
            get({
                summary = "Get execution cost analysis for all filled orders in a book"
                tags = listOf("Execution")
                request {
                    pathParameter<String>("bookId") { description = "Book identifier" }
                }
                response {
                    code(HttpStatusCode.OK) { body<List<ExecutionCostResponse>>() }
                }
            }) {
                val bookId = call.requirePathParam("bookId")
                val analyses = executionCostRepository.findByBookId(bookId)
                call.respond(analyses.map { it.toResponse() })
            }
        }

        route("/reconciliation/{bookId}") {
            get({
                summary = "Get all prime broker reconciliations for a book"
                tags = listOf("Execution")
                request {
                    pathParameter<String>("bookId") { description = "Book identifier" }
                }
                response {
                    code(HttpStatusCode.OK) { body<List<ReconciliationResponse>>() }
                }
            }) {
                val bookId = call.requirePathParam("bookId")
                val reconciliations = primeBrokerReconciliationRepository.findByBookId(bookId)
                call.respond(reconciliations.map { it.toResponse() })
            }

            post("/statements", {
                summary = "Upload a prime broker statement and run reconciliation"
                tags = listOf("Execution")
                request {
                    pathParameter<String>("bookId") { description = "Book identifier" }
                    body<PrimeBrokerStatementRequest>()
                }
                response {
                    code(HttpStatusCode.Created) { body<ReconciliationResponse>() }
                }
            }) {
                val bookId = call.requirePathParam("bookId")
                val request = call.receive<PrimeBrokerStatementRequest>()
                require(request.bookId == bookId) {
                    "bookId in path ($bookId) does not match bookId in body (${request.bookId})"
                }

                // Load internal positions for the book
                val internalPositions = positionRepository
                    .findByBookId(com.kinetix.common.model.BookId(bookId))
                    .associate { it.instrumentId.value to it.quantity }

                val pbPositions = request.positions.associate { dto ->
                    dto.instrumentId to PrimeBrokerPosition(
                        instrumentId = dto.instrumentId,
                        quantity = BigDecimal(dto.quantity),
                        price = BigDecimal(dto.price),
                    )
                }

                val reconciliation = reconciliationService.reconcile(
                    bookId = bookId,
                    date = request.date,
                    internalPositions = internalPositions,
                    pbPositions = pbPositions,
                    reconciledAt = Instant.now(),
                )

                primeBrokerReconciliationRepository.save(reconciliation, UUID.randomUUID().toString())
                call.respond(HttpStatusCode.Created, reconciliation.toResponse())
            }
        }
    }
}

// --- Mappers ---

private fun ExecutionCostAnalysis.toResponse() = ExecutionCostResponse(
    orderId = orderId,
    bookId = bookId,
    instrumentId = instrumentId,
    completedAt = completedAt.toString(),
    arrivalPrice = arrivalPrice.toPlainString(),
    averageFillPrice = averageFillPrice.toPlainString(),
    side = side.name,
    totalQty = totalQty.toPlainString(),
    slippageBps = metrics.slippageBps.toPlainString(),
    marketImpactBps = metrics.marketImpactBps?.toPlainString(),
    timingCostBps = metrics.timingCostBps?.toPlainString(),
    totalCostBps = metrics.totalCostBps.toPlainString(),
)

private fun PrimeBrokerReconciliation.toResponse() = ReconciliationResponse(
    reconciliationDate = reconciliationDate,
    bookId = bookId,
    status = status,
    totalPositions = totalPositions,
    matchedCount = matchedCount,
    breakCount = breakCount,
    breaks = breaks.map {
        ReconciliationBreakDto(
            instrumentId = it.instrumentId,
            internalQty = it.internalQty.toPlainString(),
            primeBrokerQty = it.primeBrokerQty.toPlainString(),
            breakQty = it.breakQty.toPlainString(),
            breakNotional = it.breakNotional.toPlainString(),
            severity = it.severity.name,
        )
    },
    reconciledAt = reconciledAt.toString(),
)
