package com.kinetix.regulatory.historical

data class DailyClosePrice(
    val instrumentId: String,
    val date: String,
    val closePrice: Double,
)
