package com.kinetix.position.model

import com.kinetix.common.model.Money
import com.kinetix.common.model.PortfolioId
import java.util.Currency

data class PortfolioSummary(
    val portfolioId: PortfolioId,
    val baseCurrency: Currency,
    val totalNav: Money,
    val totalUnrealizedPnl: Money,
    val currencyBreakdown: List<CurrencyExposure>,
)
