package com.kinetix.regulatory.governance.dto

import kotlinx.serialization.Serializable

@Serializable
data class ModelVersionResponse(
    val id: String,
    val modelName: String,
    val version: String,
    val status: String,
    val parameters: String,
    val approvedBy: String?,
    val approvedAt: String?,
    val createdAt: String,
)
