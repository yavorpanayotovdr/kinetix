package com.kinetix.common.model

enum class MarketDataSource(val displayName: String) {
    BLOOMBERG("Bloomberg"),
    REUTERS("Reuters"),
    EXCHANGE("Exchange"),
    INTERNAL("Internal"),
    MANUAL("Manual"),
}
