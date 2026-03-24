package com.kinetix.position.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class CollateralType(val haircut: BigDecimal) {
    CASH(BigDecimal("0.00")),
    GOVERNMENT_BOND(BigDecimal("0.03")),
    CORPORATE_BOND(BigDecimal("0.10")),
    EQUITY(BigDecimal("0.20")),
}

enum class CollateralDirection { POSTED, RECEIVED }

data class CollateralBalance(
    val id: Long? = null,
    val counterpartyId: String,
    val nettingSetId: String?,
    val collateralType: CollateralType,
    val amount: BigDecimal,
    val currency: String,
    val direction: CollateralDirection,
    val asOfDate: LocalDate,
    val valueAfterHaircut: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant,
)
