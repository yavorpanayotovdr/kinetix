package com.kinetix.common.model

import java.time.Instant
import java.time.LocalDate

data class DividendYield(
    val instrumentId: InstrumentId,
    val yield: Double,
    val exDate: LocalDate?,
    val asOfDate: Instant,
    val source: ReferenceDataSource,
)
