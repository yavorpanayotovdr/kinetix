package com.kinetix.audit.dto

import com.kinetix.audit.model.AuditEvent
import kotlinx.serialization.Serializable

@Serializable
data class AuditEventResponse(
    val id: Long,
    val tradeId: String,
    val portfolioId: String,
    val instrumentId: String,
    val assetClass: String,
    val side: String,
    val quantity: String,
    val priceAmount: String,
    val priceCurrency: String,
    val tradedAt: String,
    val receivedAt: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

fun AuditEvent.toResponse(): AuditEventResponse = AuditEventResponse(
    id = id,
    tradeId = tradeId,
    portfolioId = portfolioId,
    instrumentId = instrumentId,
    assetClass = assetClass,
    side = side,
    quantity = quantity,
    priceAmount = priceAmount,
    priceCurrency = priceCurrency,
    tradedAt = tradedAt,
    receivedAt = receivedAt.toString(),
)
