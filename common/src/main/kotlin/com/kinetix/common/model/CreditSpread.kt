package com.kinetix.common.model

import java.time.Instant

data class CreditSpread(
    val instrumentId: InstrumentId,
    val spread: Double,
    val rating: String?,
    val asOfDate: Instant,
    val source: ReferenceDataSource,
)
