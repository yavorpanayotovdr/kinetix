package com.kinetix.gateway.dto

import com.kinetix.common.model.*
import com.kinetix.gateway.client.BookTradeCommand
import com.kinetix.gateway.client.BookTradeResult
import com.kinetix.gateway.client.PortfolioSummary
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency

// --- Request DTOs ---

@Serializable
data class BookTradeRequest(
    val tradeId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
)

// --- Response DTOs ---

@Serializable
data class MoneyDto(
    val amount: String,
    val currency: String,
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
data class BookTradeResponse(
    val trade: TradeResponse,
    val position: PositionResponse,
)

@Serializable
data class PortfolioSummaryResponse(
    val portfolioId: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

// --- Domain -> DTO mappers ---

fun Money.toDto(): MoneyDto = MoneyDto(
    amount = amount.toPlainString(),
    currency = currency.currencyCode,
)

fun Trade.toResponse(): TradeResponse = TradeResponse(
    tradeId = tradeId.value,
    portfolioId = portfolioId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    side = side.name,
    quantity = quantity.toPlainString(),
    price = price.toDto(),
    tradedAt = tradedAt.toString(),
)

fun Position.toResponse(): PositionResponse = PositionResponse(
    portfolioId = portfolioId.value,
    instrumentId = instrumentId.value,
    assetClass = assetClass.name,
    quantity = quantity.toPlainString(),
    averageCost = averageCost.toDto(),
    marketPrice = marketPrice.toDto(),
    marketValue = marketValue.toDto(),
    unrealizedPnl = unrealizedPnl.toDto(),
)

fun BookTradeResult.toResponse(): BookTradeResponse = BookTradeResponse(
    trade = trade.toResponse(),
    position = position.toResponse(),
)

fun PortfolioSummary.toResponse(): PortfolioSummaryResponse = PortfolioSummaryResponse(
    portfolioId = id.value,
)

// --- DTO -> Domain mappers ---

fun BookTradeRequest.toCommand(portfolioId: PortfolioId): BookTradeCommand {
    val qty = BigDecimal(quantity)
    require(qty > BigDecimal.ZERO) { "Trade quantity must be positive, was $qty" }
    val priceAmt = BigDecimal(priceAmount)
    require(priceAmt >= BigDecimal.ZERO) { "Trade price must be non-negative, was $priceAmt" }
    return BookTradeCommand(
        tradeId = TradeId(tradeId),
        portfolioId = portfolioId,
        instrumentId = InstrumentId(instrumentId),
        assetClass = AssetClass.valueOf(assetClass),
        side = Side.valueOf(side),
        quantity = qty,
        price = Money(priceAmt, Currency.getInstance(priceCurrency)),
        tradedAt = Instant.parse(tradedAt),
    )
}
