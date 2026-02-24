package com.kinetix.price.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestPriceRequest(
    val instrumentId: String,
    val priceAmount: String,
    val priceCurrency: String,
    val source: String,
)
