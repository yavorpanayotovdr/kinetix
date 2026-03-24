package com.kinetix.risk.model

data class FactorContribution(
    val factorType: String,
    val varContribution: Double,
    val pctOfTotal: Double,
    val loading: Double,
    val loadingMethod: String,
)
