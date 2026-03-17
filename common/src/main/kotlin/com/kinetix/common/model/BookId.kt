package com.kinetix.common.model

data class BookId(val value: String) {
    init {
        require(value.isNotBlank()) { "BookId must not be blank" }
    }
}
