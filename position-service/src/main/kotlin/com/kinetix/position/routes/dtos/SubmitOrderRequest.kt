package com.kinetix.position.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class SubmitOrderRequest(
    val bookId: String,
    val instrumentId: String,
    val side: String,
    val quantity: String,
    val orderType: String,
    val limitPrice: String? = null,
    val arrivalPrice: String,
    val fixSessionId: String? = null,
)
