package com.kinetix.regulatory.historical

import java.math.BigDecimal

data class HistoricalScenarioReturn(
    val periodId: String,
    val instrumentId: String,
    val returnDate: String,
    val dailyReturn: BigDecimal,
    val source: String = "HISTORICAL",
)
