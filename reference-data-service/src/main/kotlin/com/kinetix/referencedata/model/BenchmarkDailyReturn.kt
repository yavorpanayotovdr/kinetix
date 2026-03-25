package com.kinetix.referencedata.model

import java.math.BigDecimal
import java.time.LocalDate

data class BenchmarkDailyReturn(
    val benchmarkId: String,
    val returnDate: LocalDate,
    val dailyReturn: BigDecimal,
)
