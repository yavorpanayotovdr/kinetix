package com.kinetix.risk.model

data class FetchSuccess(
    override val dependency: DiscoveredDependency,
    val value: MarketDataValue,
) : FetchResult
