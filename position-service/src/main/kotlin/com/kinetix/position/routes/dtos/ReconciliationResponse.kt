package com.kinetix.position.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ReconciliationBreakDto(
    val instrumentId: String,
    val internalQty: String,
    val primeBrokerQty: String,
    val breakQty: String,
    val breakNotional: String,
    val severity: String,
)

@Serializable
data class ReconciliationResponse(
    val reconciliationDate: String,
    val bookId: String,
    val status: String,
    val totalPositions: Int,
    val matchedCount: Int,
    val breakCount: Int,
    val breaks: List<ReconciliationBreakDto>,
    val reconciledAt: String,
)
