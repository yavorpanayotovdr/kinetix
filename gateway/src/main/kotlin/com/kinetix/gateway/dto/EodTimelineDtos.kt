package com.kinetix.gateway.dto

import com.kinetix.gateway.client.EodTimelineEntryItem
import com.kinetix.gateway.client.EodTimelineSummary
import kotlinx.serialization.Serializable

@Serializable
data class EodTimelineEntryResponse(
    val valuationDate: String,
    val jobId: String,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    val pvValue: Double? = null,
    val delta: Double? = null,
    val gamma: Double? = null,
    val vega: Double? = null,
    val theta: Double? = null,
    val rho: Double? = null,
    val promotedAt: String? = null,
    val promotedBy: String? = null,
    val varChange: Double? = null,
    val varChangePct: Double? = null,
    val esChange: Double? = null,
    val calculationType: String? = null,
    val confidenceLevel: Double? = null,
)

@Serializable
data class EodTimelineResponse(
    val bookId: String,
    val from: String,
    val to: String,
    val entries: List<EodTimelineEntryResponse>,
)

fun EodTimelineEntryItem.toResponse() = EodTimelineEntryResponse(
    valuationDate = valuationDate,
    jobId = jobId,
    varValue = varValue,
    expectedShortfall = expectedShortfall,
    pvValue = pvValue,
    delta = delta,
    gamma = gamma,
    vega = vega,
    theta = theta,
    rho = rho,
    promotedAt = promotedAt,
    promotedBy = promotedBy,
    varChange = varChange,
    varChangePct = varChangePct,
    esChange = esChange,
    calculationType = calculationType,
    confidenceLevel = confidenceLevel,
)

fun EodTimelineSummary.toResponse() = EodTimelineResponse(
    bookId = bookId,
    from = from,
    to = to,
    entries = entries.map { it.toResponse() },
)
