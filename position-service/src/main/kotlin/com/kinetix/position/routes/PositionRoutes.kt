package com.kinetix.position.routes

import com.kinetix.common.model.*
import com.kinetix.position.persistence.PositionRepository
import com.kinetix.position.service.BookTradeCommand
import com.kinetix.position.service.GetPositionsQuery
import com.kinetix.position.service.PositionQueryService
import com.kinetix.position.service.TradeBookingService
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
)

// --- Routes ---

fun Route.positionRoutes(
    positionRepository: PositionRepository,
    positionQueryService: PositionQueryService,
    tradeBookingService: TradeBookingService,
) {
    route("/api/v1/portfolios") {

        get {
            val portfolioIds = positionRepository.findDistinctPortfolioIds()
            call.respond(portfolioIds.map { PortfolioSummaryResponse(it.value) })
        }

        route("/{portfolioId}") {

            get("/positions") {
                val portfolioId = PortfolioId(call.parameters["portfolioId"]!!)
                val positions = positionQueryService.handle(GetPositionsQuery(portfolioId))
                call.respond(positions.map { it.toResponse() })
            }

            post("/trades") {
                val portfolioId = PortfolioId(call.parameters["portfolioId"]!!)
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
        }
    }
}
