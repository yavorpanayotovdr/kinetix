package com.kinetix.risk.model

enum class MarketRegime {
    NORMAL,
    ELEVATED_VOL,
    CRISIS,
    RECOVERY;

    val severity: Int get() = when (this) {
        NORMAL -> 0
        RECOVERY -> 1
        ELEVATED_VOL -> 2
        CRISIS -> 3
    }
}
