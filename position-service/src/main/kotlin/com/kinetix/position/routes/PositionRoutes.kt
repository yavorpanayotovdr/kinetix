package com.kinetix.position.routes

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.service.*
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import java.util.UUID

// --- Response DTOs matching gateway's expected JSON shapes ---

@Serializable
data class MoneyDto(
    val amount: String,
    val currency: String,
)

@Serializable
data class PortfolioSummaryResponse(
    val portfolioId: String,
)

@Serializable
data class PositionResponse(
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val quantity: String,
    val averageCost: MoneyDto,
    val marketPrice: MoneyDto,
    val marketValue: MoneyDto,
    val unrealizedPnl: MoneyDto,
    val realizedPnl: MoneyDto,
)

@Serializable
data class TradeResponse(
    val tradeId: String,
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val price: MoneyDto,
    val tradedAt: String,
    val type: String = "NEW",
    val status: String = "LIVE",
    val originalTradeId: String? = null,
)

@Serializable
data class AmendTradeRequest(
    val newTradeId: String? = null,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
)

@Serializable
data class BookTradeResponse(
    val trade: TradeResponse,
    val position: PositionResponse,
)

@Serializable
data class BookTradeRequest(
    val tradeId: String? = null,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

// --- Mappers ---

private fun Money.toDto() = MoneyDto(amount.toPlainString(), currency.currencyCode)

private fun Position.toResponse() = PositionResponse(
    portfolioId = portfolioId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    quantity = quantity.toPlainString(),
    averageCost = averageCost.toDto(),
    marketPrice = marketPrice.toDto(),
    marketValue = marketValue.toDto(),
    unrealizedPnl = unrealizedPnl.toDto(),
    realizedPnl = realizedPnl.toDto(),
)

private fun Trade.toResponse() = TradeResponse(
    tradeId = tradeId.value,
    portfolioId = portfolioId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    side = side.name,
    quantity = quantity.toPlainString(),
    price = price.toDto(),
    tradedAt = tradedAt.toString(),
    type = type.name,
    status = status.name,
    originalTradeId = originalTradeId?.value,
)

// --- Routes ---

fun Route.positionRoutes(
    positionRepository: PositionRepository,
    positionQueryService: PositionQueryService,
    tradeBookingService: TradeBookingService,
    tradeEventRepository: com.kinetix.position.persistence.TradeEventRepository,
    tradeLifecycleService: TradeLifecycleService,
) {
    route("/api/v1/portfolios") {

        get({
            summary = "List all portfolios"
            tags = listOf("Portfolios")
            response {
                code(HttpStatusCode.OK) { body<List<PortfolioSummaryResponse>>() }
            }
        }) {
            val portfolioIds = positionRepository.findDistinctPortfolioIds()
            call.respond(portfolioIds.map { PortfolioSummaryResponse(it.value) })
        }

        route("/{portfolioId}") {

            get("/trades", {
                summary = "Get trade history for a portfolio"
                tags = listOf("Trades")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                }
                response {
                    code(HttpStatusCode.OK) { body<List<TradeResponse>>() }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val trades = tradeEventRepository.findByPortfolioId(portfolioId)
                call.respond(trades.map { it.toResponse() })
            }

            get("/positions", {
                summary = "Get positions for a portfolio"
                tags = listOf("Positions")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                }
                response {
                    code(HttpStatusCode.OK) { body<List<PositionResponse>>() }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val positions = positionQueryService.handle(GetPositionsQuery(portfolioId))
                call.respond(positions.map { it.toResponse() })
            }

            post("/trades", {
                summary = "Book a trade"
                tags = listOf("Trades")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                    body<BookTradeRequest>()
                }
                response {
                    code(HttpStatusCode.Created) { body<BookTradeResponse>() }
                    code(HttpStatusCode.BadRequest) { body<ErrorResponse>() }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val request = call.receive<BookTradeRequest>()
                val qty = BigDecimal(request.quantity)
                require(qty > BigDecimal.ZERO) { "Trade quantity must be positive, was $qty" }
                val priceAmt = BigDecimal(request.priceAmount)
                require(priceAmt >= BigDecimal.ZERO) { "Trade price must be non-negative, was $priceAmt" }
                val command = BookTradeCommand(
                    tradeId = TradeId(request.tradeId ?: UUID.randomUUID().toString()),
                    portfolioId = portfolioId,
                    instrumentId = InstrumentId(request.instrumentId),
                    assetClass = AssetClass.valueOf(request.assetClass),
                    side = Side.valueOf(request.side),
                    quantity = qty,
                    price = Money(priceAmt, Currency.getInstance(request.priceCurrency)),
                    tradedAt = Instant.parse(request.tradedAt),
                )
                val result = tradeBookingService.handle(command)
                call.respond(
                    HttpStatusCode.Created,
                    BookTradeResponse(
                        trade = result.trade.toResponse(),
                        position = result.position.toResponse(),
                    ),
                )
            }

            put("/trades/{tradeId}", {
                summary = "Amend a trade"
                tags = listOf("Trades")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                    pathParameter<String>("tradeId") { description = "Trade identifier to amend" }
                    body<AmendTradeRequest>()
                }
                response {
                    code(HttpStatusCode.OK) { body<BookTradeResponse>() }
                    code(HttpStatusCode.BadRequest) { body<ErrorResponse>() }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val tradeId = TradeId(call.requirePathParam("tradeId"))
                val request = call.receive<AmendTradeRequest>()
                val qty = BigDecimal(request.quantity)
                require(qty > BigDecimal.ZERO) { "Trade quantity must be positive, was $qty" }
                val priceAmt = BigDecimal(request.priceAmount)
                require(priceAmt >= BigDecimal.ZERO) { "Trade price must be non-negative, was $priceAmt" }
                val command = AmendTradeCommand(
                    originalTradeId = tradeId,
                    newTradeId = TradeId(request.newTradeId ?: UUID.randomUUID().toString()),
                    portfolioId = portfolioId,
                    instrumentId = InstrumentId(request.instrumentId),
                    assetClass = AssetClass.valueOf(request.assetClass),
                    side = Side.valueOf(request.side),
                    quantity = qty,
                    price = Money(priceAmt, Currency.getInstance(request.priceCurrency)),
                    tradedAt = Instant.parse(request.tradedAt),
                )
                val result = tradeLifecycleService.handleAmend(command)
                call.respond(
                    HttpStatusCode.OK,
                    BookTradeResponse(
                        trade = result.trade.toResponse(),
                        position = result.position.toResponse(),
                    ),
                )
            }

            delete("/trades/{tradeId}", {
                summary = "Cancel a trade"
                tags = listOf("Trades")
                request {
                    pathParameter<String>("portfolioId") { description = "Portfolio identifier" }
                    pathParameter<String>("tradeId") { description = "Trade identifier to cancel" }
                }
                response {
                    code(HttpStatusCode.OK) { body<BookTradeResponse>() }
                    code(HttpStatusCode.BadRequest) { body<ErrorResponse>() }
                }
            }) {
                val portfolioId = PortfolioId(call.requirePathParam("portfolioId"))
                val tradeId = TradeId(call.requirePathParam("tradeId"))
                val command = CancelTradeCommand(
                    tradeId = tradeId,
                    portfolioId = portfolioId,
                )
                val result = tradeLifecycleService.handleCancel(command)
                call.respond(
                    HttpStatusCode.OK,
                    BookTradeResponse(
                        trade = result.trade.toResponse(),
                        position = result.position.toResponse(),
                    ),
                )
            }
        }
    }
}
