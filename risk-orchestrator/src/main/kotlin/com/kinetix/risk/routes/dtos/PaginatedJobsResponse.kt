package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedJobsResponse(
    val items: List<ValuationJobSummaryResponse>,
    val totalCount: Long,
)
