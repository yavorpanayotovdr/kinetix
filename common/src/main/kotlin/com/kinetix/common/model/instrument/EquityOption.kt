package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("EQUITY_OPTION")
data class EquityOption(
    val underlyingId: String,
    val optionType: String,
    val strike: Double,
    val expiryDate: String,
    val exerciseStyle: String,
    val contractMultiplier: Double = 100.0,
    val dividendYield: Double = 0.0,
) : InstrumentType {
    override val instrumentTypeName: String get() = "EQUITY_OPTION"
    override fun assetClass(): AssetClass = AssetClass.EQUITY
}
