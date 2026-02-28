package com.kinetix.risk.client.dtos

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.Money
import com.kinetix.common.model.PortfolioId
import com.kinetix.common.model.Position
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class PositionDto(
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val quantity: String,
    val averageCost: MoneyDto,
    val marketPrice: MoneyDto,
    val marketValue: MoneyDto,
    val unrealizedPnl: MoneyDto,
) {
    fun toDomain(): Position = Position(
        portfolioId = PortfolioId(portfolioId),
        instrumentId = InstrumentId(instrumentId),
        assetClass = AssetClass.valueOf(assetClass),
        quantity = BigDecimal(quantity),
        averageCost = averageCost.toDomain(),
        marketPrice = marketPrice.toDomain(),
    )
}
