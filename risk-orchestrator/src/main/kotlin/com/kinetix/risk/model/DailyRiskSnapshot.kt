package com.kinetix.risk.model

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.PortfolioId
import java.math.BigDecimal
import java.time.LocalDate

data class DailyRiskSnapshot(
    val id: Long? = null,
    val portfolioId: PortfolioId,
    val snapshotDate: LocalDate,
    val instrumentId: InstrumentId,
    val assetClass: AssetClass,
    val quantity: BigDecimal,
    val marketPrice: BigDecimal,
    val delta: Double? = null,
    val gamma: Double? = null,
    val vega: Double? = null,
    val theta: Double? = null,
    val rho: Double? = null,
)
