package com.kinetix.common.model

data class TradeId(val value: String) {
    init {
        require(value.isNotBlank()) { "TradeId must not be blank" }
    }
}
