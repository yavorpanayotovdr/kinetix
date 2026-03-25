package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class IntradayVaRTimelineResponse(
    val bookId: String,
    val varPoints: List<IntradayVaRPointDto>,
    val tradeAnnotations: List<TradeAnnotationDto>,
)
