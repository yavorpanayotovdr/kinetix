package com.kinetix.regulatory.governance.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterModelRequest(
    val modelName: String,
    val version: String,
    val parameters: String,
)
