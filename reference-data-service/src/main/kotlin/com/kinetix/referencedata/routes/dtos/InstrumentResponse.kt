package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class InstrumentResponse(
    val instrumentId: String,
    val instrumentType: String,
    val displayName: String,
    val assetClass: String,
    val currency: String,
    val attributes: JsonObject,
    val createdAt: String,
    val updatedAt: String,
)
