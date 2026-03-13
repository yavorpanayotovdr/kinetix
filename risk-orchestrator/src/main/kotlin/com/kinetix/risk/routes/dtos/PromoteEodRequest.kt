package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PromoteEodRequest(
    val label: String,
    val promotedBy: String,
)
