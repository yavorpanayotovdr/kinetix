package com.kinetix.gateway.dto

import com.kinetix.common.model.PricePoint
import kotlinx.serialization.Serializable

@Serializable
data class PricePointResponse(
    val instrumentId: String,
    val price: MoneyDto,
    val timestamp: String,
    val source: String,
)

fun PricePoint.toResponse(): PricePointResponse = PricePointResponse(
    instrumentId = instrumentId.value,
    price = price.toDto(),
    timestamp = timestamp.toString(),
    source = source.name,
)
