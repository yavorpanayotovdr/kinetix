package com.kinetix.risk.model

import java.math.BigDecimal

data class InstrumentKrdResult(
    val instrumentId: String,
    val krdBuckets: List<KrdBucket>,
    val totalDv01: BigDecimal,
)
