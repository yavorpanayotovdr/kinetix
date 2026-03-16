package com.kinetix.common.model.instrument

import com.kinetix.common.model.AssetClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("CASH_EQUITY")
data class CashEquity(
    val currency: String,
    val exchange: String? = null,
    val sector: String? = null,
    val countryCode: String? = null,
) : InstrumentType {
    override val instrumentTypeName: String get() = "CASH_EQUITY"
    override fun assetClass(): AssetClass = AssetClass.EQUITY
}
