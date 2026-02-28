package com.kinetix.risk.client.dtos

import com.kinetix.common.model.PortfolioId
import kotlinx.serialization.Serializable

@Serializable
data class PortfolioSummaryDto(
    val portfolioId: String,
) {
    fun toDomain(): PortfolioId = PortfolioId(portfolioId)
}
