package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String)
