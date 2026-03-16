package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("FX_FORWARD")
data class FxForward(
    val baseCurrency: String,
    val quoteCurrency: String,
    val deliveryDate: String,
    val forwardRate: Double? = null,
) : InstrumentType {
    override val instrumentTypeName: String get() = "FX_FORWARD"
    override fun assetClass(): AssetClass = AssetClass.FX
}
