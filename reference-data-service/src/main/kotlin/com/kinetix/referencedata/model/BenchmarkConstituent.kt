package com.kinetix.referencedata.model

import java.math.BigDecimal
import java.time.LocalDate

data class BenchmarkConstituent(
    val benchmarkId: String,
    val instrumentId: String,
    val weight: BigDecimal,
    val asOfDate: LocalDate,
)
