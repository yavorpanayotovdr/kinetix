package com.kinetix.risk.model

data class PortfolioDiff(
    val varChange: Double,
    val varChangePercent: Double?,
    val esChange: Double,
    val esChangePercent: Double?,
    val pvChange: Double,
    val deltaChange: Double,
    val gammaChange: Double,
    val vegaChange: Double,
    val thetaChange: Double,
    val rhoChange: Double,
)
