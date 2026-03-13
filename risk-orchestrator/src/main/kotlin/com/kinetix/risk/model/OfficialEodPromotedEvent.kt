package com.kinetix.risk.model

import kotlinx.serialization.Serializable

@Serializable
data class OfficialEodPromotedEvent(
    val jobId: String,
    val portfolioId: String,
    val valuationDate: String,
    val promotedBy: String,
    val promotedAt: String,
    val varValue: Double?,
    val expectedShortfall: Double?,
)
