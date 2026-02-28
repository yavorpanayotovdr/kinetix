package com.kinetix.risk.model

import com.kinetix.common.model.PortfolioId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SodBaseline(
    val id: Long? = null,
    val portfolioId: PortfolioId,
    val baselineDate: LocalDate,
    val snapshotType: SnapshotType,
    val createdAt: Instant,
    val sourceJobId: UUID? = null,
    val calculationType: String? = null,
)
