package com.kinetix.position.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class StrategyResponse(
    val strategyId: String,
    val bookId: String,
    val strategyType: String,
    val name: String? = null,
    val createdAt: String,
)
