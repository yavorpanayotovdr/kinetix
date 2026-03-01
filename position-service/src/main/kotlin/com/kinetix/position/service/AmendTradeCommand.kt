package com.kinetix.position.service

import com.kinetix.common.model.*
import java.math.BigDecimal
import java.time.Instant

data class AmendTradeCommand(
    val originalTradeId: TradeId,
    val newTradeId: TradeId,
    val portfolioId: PortfolioId,
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val side: Side,
    val quantity: BigDecimal,
    val price: Money,
    val tradedAt: Instant,
)
