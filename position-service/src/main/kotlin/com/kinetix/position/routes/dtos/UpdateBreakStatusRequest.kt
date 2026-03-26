package com.kinetix.position.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class UpdateBreakStatusRequest(
    val status: String,
)
