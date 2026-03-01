package com.kinetix.common.model

import java.math.BigDecimal
import java.time.Instant

data class Trade(
    val tradeId: TradeId,
    val portfolioId: PortfolioId,
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val side: Side,
    val quantity: BigDecimal,
    val price: Money,
    val tradedAt: Instant,
    val type: TradeType = TradeType.NEW,
    val status: TradeStatus = TradeStatus.LIVE,
    val originalTradeId: TradeId? = null,
) {
    init {
        require(quantity > BigDecimal.ZERO) { "Trade quantity must be positive, was $quantity" }
        require(price.amount >= BigDecimal.ZERO) { "Trade price must be non-negative, was ${price.amount}" }
    }

    val notional: Money
        get() = price * quantity

    val signedQuantity: BigDecimal
        get() = quantity * side.sign.toBigDecimal()
}
