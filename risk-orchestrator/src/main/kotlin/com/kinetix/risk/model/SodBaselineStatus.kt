package com.kinetix.risk.model

import java.time.Instant

data class SodBaselineStatus(
    val exists: Boolean,
    val baselineDate: String? = null,
    val snapshotType: SnapshotType? = null,
    val createdAt: Instant? = null,
    val sourceJobId: String? = null,
    val calculationType: String? = null,
)
