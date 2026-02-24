package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IngestDividendYieldRequest(
    val instrumentId: String,
    val yield: Double,
    val exDate: String? = null,
    val source: String,
)
