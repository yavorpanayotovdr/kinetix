package com.kinetix.referencedata.model

import java.math.BigDecimal
import java.time.Instant

data class Counterparty(
    val counterpartyId: String,
    val legalName: String,
    val shortName: String,
    val lei: String?,
    val ratingSp: String?,
    val ratingMoodys: String?,
    val ratingFitch: String?,
    val sector: String,
    val country: String?,
    val isFinancial: Boolean,
    val pd1y: BigDecimal?,
    val lgd: BigDecimal,
    val cdsSpreadBps: BigDecimal?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
