package com.kinetix.regulatory.governance

import java.time.Instant

data class ModelVersion(
    val id: String,
    val modelName: String,
    val version: String,
    val status: ModelVersionStatus,
    val parameters: String,
    val approvedBy: String?,
    val approvedAt: Instant?,
    val createdAt: Instant,
)
