package com.kinetix.risk.model

import com.kinetix.common.model.BookId

data class CrossBookVaRRequest(
    val bookIds: List<BookId>,
    val portfolioGroupId: String,
    val calculationType: CalculationType,
    val confidenceLevel: ConfidenceLevel,
    val timeHorizonDays: Int = 1,
    val numSimulations: Int = 10_000,
    val monteCarloSeed: Long = 0,
)
