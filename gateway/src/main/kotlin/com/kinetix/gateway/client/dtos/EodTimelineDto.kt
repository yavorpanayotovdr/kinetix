package com.kinetix.gateway.client.dtos

import com.kinetix.gateway.client.EodTimelineEntryItem
import com.kinetix.gateway.client.EodTimelineSummary
import kotlinx.serialization.Serializable

@Serializable
data class EodTimelineEntryClientDto(
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
data class EodTimelineClientDto(
    val bookId: String,
    val from: String,
    val to: String,
    val entries: List<EodTimelineEntryClientDto>,
)

fun EodTimelineEntryClientDto.toDomain() = EodTimelineEntryItem(
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

fun EodTimelineClientDto.toDomain() = EodTimelineSummary(
    bookId = bookId,
    from = from,
    to = to,
    entries = entries.map { it.toDomain() },
)
