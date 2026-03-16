package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("EQUITY_FUTURE")
data class EquityFuture(
    val underlyingId: String,
    val expiryDate: String,
    val contractSize: Double,
    val currency: String,
) : InstrumentType {
    override val instrumentTypeName: String get() = "EQUITY_FUTURE"
    override fun assetClass(): AssetClass = AssetClass.EQUITY
}
