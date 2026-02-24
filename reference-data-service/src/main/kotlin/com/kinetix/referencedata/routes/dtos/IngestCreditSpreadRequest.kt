package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestCreditSpreadRequest(
    val instrumentId: String,
    val spread: Double,
    val rating: String? = null,
    val source: String,
)
