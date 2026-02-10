package com.kinetix.common.model

data class PortfolioId(val value: String) {
    init {
        require(value.isNotBlank()) { "PortfolioId must not be blank" }
    }
}
