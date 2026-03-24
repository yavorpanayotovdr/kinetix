package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IntradayPnlSeriesResponse(
    val bookId: String,
    val snapshots: List<IntradayPnlSnapshotDto>,
)
