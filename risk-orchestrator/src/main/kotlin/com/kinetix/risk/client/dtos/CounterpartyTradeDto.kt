package com.kinetix.risk.client.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CounterpartyTradeDto(
    val tradeId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val instrumentType: String? = null,
    val counterpartyId: String? = null,
)
