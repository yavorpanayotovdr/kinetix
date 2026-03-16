package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("COMMODITY_FUTURE")
data class CommodityFuture(
    val commodity: String,
    val expiryDate: String,
    val contractSize: Double,
    val currency: String,
) : InstrumentType {
    override val instrumentTypeName: String get() = "COMMODITY_FUTURE"
    override fun assetClass(): AssetClass = AssetClass.COMMODITY
}
