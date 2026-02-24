package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreditSpreadResponse(
    val instrumentId: String,
    val spread: Double,
    val rating: String?,
    val asOfDate: String,
    val source: String,
)
