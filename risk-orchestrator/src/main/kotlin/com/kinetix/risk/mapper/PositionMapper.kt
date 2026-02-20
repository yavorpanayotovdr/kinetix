package com.kinetix.risk.mapper

import com.kinetix.common.model.AssetClass
import com.kinetix.common.model.Position
import com.kinetix.proto.common.AssetClass as ProtoAssetClass
import com.kinetix.proto.common.InstrumentId as ProtoInstrumentId
import com.kinetix.proto.common.Money as ProtoMoney
import com.kinetix.proto.common.PortfolioId as ProtoPortfolioId
import com.kinetix.proto.common.Position as ProtoPosition

private val ASSET_CLASS_TO_PROTO = mapOf(
    AssetClass.EQUITY to ProtoAssetClass.EQUITY,
    AssetClass.FIXED_INCOME to ProtoAssetClass.FIXED_INCOME,
    AssetClass.FX to ProtoAssetClass.FX,
    AssetClass.COMMODITY to ProtoAssetClass.COMMODITY,
    AssetClass.DERIVATIVE to ProtoAssetClass.DERIVATIVE,
)

fun Position.toProto(): ProtoPosition = ProtoPosition.newBuilder()
    .setPortfolioId(ProtoPortfolioId.newBuilder().setValue(portfolioId.value))
    .setInstrumentId(ProtoInstrumentId.newBuilder().setValue(instrumentId.value))
    .setAssetClass(ASSET_CLASS_TO_PROTO.getValue(assetClass))
    .setQuantity(quantity.toDouble())
    .setMarketValue(
        ProtoMoney.newBuilder()
            .setAmount(marketValue.amount.toPlainString())
            .setCurrency(marketValue.currency.currencyCode)
    )
    .build()
