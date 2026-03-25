package com.kinetix.position.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateStrategyRequest(
    val strategyType: String,
    val name: String? = null,
)
