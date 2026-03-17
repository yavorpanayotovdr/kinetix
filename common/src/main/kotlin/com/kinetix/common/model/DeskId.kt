package com.kinetix.common.model

data class DeskId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeskId must not be blank" }
    }
}
