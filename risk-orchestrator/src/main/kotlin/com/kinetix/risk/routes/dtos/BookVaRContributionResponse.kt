package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class BookVaRContributionResponse(
    val bookId: String,
    val varContribution: String,
    val percentageOfTotal: String,
    val standaloneVar: String,
    val diversificationBenefit: String,
)
