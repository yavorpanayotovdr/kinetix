package com.kinetix.risk.model

import com.kinetix.common.model.BookId

data class BookVaRContribution(
    val bookId: BookId,
    val varContribution: Double,
    val percentageOfTotal: Double,
    val standaloneVar: Double,
    val diversificationBenefit: Double,
    val marginalVar: Double = 0.0,
    val incrementalVar: Double = 0.0,
)
