package com.kinetix.risk.model

import com.kinetix.common.model.BookId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SodBaseline(
    val id: Long? = null,
    val bookId: BookId,
    val baselineDate: LocalDate,
    val snapshotType: SnapshotType,
    val createdAt: Instant,
    val sourceJobId: UUID? = null,
    val calculationType: String? = null,
    val varValue: Double? = null,
    val expectedShortfall: Double? = null,
    /** FK to sod_greek_snapshots.id — null until the SOD Greek snapshot job completes. */
    val greekSnapshotId: Long? = null,
)
