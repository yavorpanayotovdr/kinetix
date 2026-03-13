package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PortfolioDiffResponse(
    val varChange: String,
    val varChangePercent: String?,
    val esChange: String,
    val esChangePercent: String?,
    val pvChange: String,
    val deltaChange: String,
    val gammaChange: String,
    val vegaChange: String,
    val thetaChange: String,
    val rhoChange: String,
)
