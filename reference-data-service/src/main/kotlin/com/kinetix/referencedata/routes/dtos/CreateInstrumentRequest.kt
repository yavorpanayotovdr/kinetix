package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CreateInstrumentRequest(
    val instrumentId: String,
    val instrumentType: String,
    val displayName: String,
    val currency: String,
    val attributes: JsonObject,
)
