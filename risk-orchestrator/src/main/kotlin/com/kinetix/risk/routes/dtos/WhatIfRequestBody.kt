package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class WhatIfRequestBody(
    val hypotheticalTrades: List<HypotheticalTradeDto>,
    val calculationType: String? = null,
    val confidenceLevel: String? = null,
)
