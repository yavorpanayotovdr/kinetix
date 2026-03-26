package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class FactorDefinitionResponse(
    val factorName: String,
    val proxyInstrumentId: String,
    val description: String,
)
