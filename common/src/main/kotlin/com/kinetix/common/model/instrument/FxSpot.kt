package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("FX_SPOT")
data class FxSpot(
    val baseCurrency: String,
    val quoteCurrency: String,
) : InstrumentType {
    override val instrumentTypeName: String get() = "FX_SPOT"
    override fun assetClass(): AssetClass = AssetClass.FX
}
