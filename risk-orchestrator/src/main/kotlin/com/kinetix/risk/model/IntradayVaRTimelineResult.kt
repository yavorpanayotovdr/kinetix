package com.kinetix.risk.model

data class IntradayVaRTimelineResult(
    val varPoints: List<IntradayVaRPoint>,
    val tradeAnnotations: List<TradeAnnotation>,
)
