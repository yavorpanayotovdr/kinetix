package com.kinetix.common.model

enum class Side {
    BUY,
    SELL;

    val sign: Int
        get() = when (this) {
            BUY -> 1
            SELL -> -1
        }
}
