package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class AcceptHedgeRequestBody(
    val acceptedBy: String,
    val suggestionIndices: List<Int>? = null,
)
