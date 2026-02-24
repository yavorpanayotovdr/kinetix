package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DividendYieldResponse(
    val instrumentId: String,
    val yield: Double,
    val exDate: String?,
    val asOfDate: String,
    val source: String,
)
