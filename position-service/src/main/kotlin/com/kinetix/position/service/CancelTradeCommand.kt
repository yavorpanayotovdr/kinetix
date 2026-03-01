package com.kinetix.position.service

import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.TradeId

data class CancelTradeCommand(
    val tradeId: TradeId,
    val portfolioId: PortfolioId,
)
