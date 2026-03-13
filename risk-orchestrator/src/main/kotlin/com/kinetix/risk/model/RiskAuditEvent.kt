package com.kinetix.risk.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface RiskAuditEvent {
    val eventType: String
    val jobId: String
    val portfolioId: String
    val valuationDate: String
    val manifestId: String
}

@Serializable
data class ManifestFrozenEvent(
    override val jobId: String,
    override val portfolioId: String,
    override val valuationDate: String,
    override val manifestId: String,
    val capturedAt: String,
    val positionCount: Int,
    val positionDigest: String,
    val marketDataDigest: String,
    val inputDigest: String,
    val modelVersion: String,
    val calculationType: String,
    val status: String,
) : RiskAuditEvent {
    override val eventType: String = "RISK_RUN_MANIFEST_FROZEN"
}

@Serializable
data class EodPromotedAuditEvent(
    override val jobId: String,
    override val portfolioId: String,
    override val valuationDate: String,
    override val manifestId: String,
    val promotedBy: String,
    val promotedAt: String,
    val varValue: Double?,
    val expectedShortfall: Double?,
) : RiskAuditEvent {
    override val eventType: String = "RISK_RUN_EOD_PROMOTED"
}
