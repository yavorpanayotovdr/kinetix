package com.kinetix.position.routes

import com.kinetix.position.model.CollateralBalance
import com.kinetix.position.model.CollateralDirection
import com.kinetix.position.model.CollateralType
import com.kinetix.position.service.CollateralTrackingService
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate

@Serializable
data class PostCollateralRequest(
    val nettingSetId: String? = null,
    val collateralType: String,
    val amount: String,
    val currency: String,
    val direction: String,
    val asOfDate: String? = null,
)

@Serializable
data class CollateralBalanceResponse(
    val id: Long?,
    val counterpartyId: String,
    val nettingSetId: String?,
    val collateralType: String,
    val amount: String,
    val currency: String,
    val direction: String,
    val asOfDate: String,
    val valueAfterHaircut: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class NetCollateralResponse(
    val counterpartyId: String,
    val collateralReceived: String,
    val collateralPosted: String,
)

private fun CollateralBalance.toResponse() = CollateralBalanceResponse(
    id = id,
    counterpartyId = counterpartyId,
    nettingSetId = nettingSetId,
    collateralType = collateralType.name,
    amount = amount.toPlainString(),
    currency = currency,
    direction = direction.name,
    asOfDate = asOfDate.toString(),
    valueAfterHaircut = valueAfterHaircut.toPlainString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun Route.collateralRoutes(collateralTrackingService: CollateralTrackingService) {
    route("/api/v1/counterparties/{counterpartyId}/collateral") {

        post({
            summary = "Post collateral for a counterparty"
            tags = listOf("Collateral")
            request {
                pathParameter<String>("counterpartyId") { description = "Counterparty ID" }
                body<PostCollateralRequest>()
            }
            response {
                code(HttpStatusCode.Created) { body<CollateralBalanceResponse>() }
                code(HttpStatusCode.BadRequest) { body<ErrorResponse>() }
            }
        }) {
            val counterpartyId = call.requirePathParam("counterpartyId")
            val request = call.receive<PostCollateralRequest>()

            val collateralType = runCatching { CollateralType.valueOf(request.collateralType) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_collateral_type", "Unknown collateral type: ${request.collateralType}"),
                )

            val direction = runCatching { CollateralDirection.valueOf(request.direction) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_direction", "Unknown direction: ${request.direction}"),
                )

            val amount = runCatching { BigDecimal(request.amount) }.getOrNull()
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_amount", "Amount must be a valid decimal"),
                )

            val asOfDate = request.asOfDate
                ?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("invalid_date", "asOfDate must be ISO-8601 (yyyy-MM-dd)"),
                        )
                }
                ?: LocalDate.now()

            val balance = runCatching {
                collateralTrackingService.postCollateral(
                    counterpartyId = counterpartyId,
                    nettingSetId = request.nettingSetId,
                    collateralType = collateralType,
                    amount = amount,
                    currency = request.currency,
                    direction = direction,
                    asOfDate = asOfDate,
                )
            }.getOrElse { ex ->
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("invalid_request", ex.message ?: "Invalid request"),
                )
            }

            call.respond(HttpStatusCode.Created, balance.toResponse())
        }

        get({
            summary = "List collateral balances for a counterparty"
            tags = listOf("Collateral")
            request {
                pathParameter<String>("counterpartyId") { description = "Counterparty ID" }
            }
            response {
                code(HttpStatusCode.OK) { body<List<CollateralBalanceResponse>>() }
            }
        }) {
            val counterpartyId = call.requirePathParam("counterpartyId")
            val balances = collateralTrackingService.getCollateralForCounterparty(counterpartyId)
            call.respond(balances.map { it.toResponse() })
        }

        get("/net", {
            summary = "Get net collateral position for a counterparty"
            tags = listOf("Collateral")
            request {
                pathParameter<String>("counterpartyId") { description = "Counterparty ID" }
            }
            response {
                code(HttpStatusCode.OK) { body<NetCollateralResponse>() }
            }
        }) {
            val counterpartyId = call.requirePathParam("counterpartyId")
            val (received, posted) = collateralTrackingService.netCollateral(counterpartyId)
            call.respond(
                NetCollateralResponse(
                    counterpartyId = counterpartyId,
                    collateralReceived = received.toPlainString(),
                    collateralPosted = posted.toPlainString(),
                ),
            )
        }
    }
}
