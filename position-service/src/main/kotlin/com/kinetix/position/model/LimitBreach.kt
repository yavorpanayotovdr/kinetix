package com.kinetix.position.model

data class LimitBreach(
    val limitType: String,
    val severity: LimitBreachSeverity,
    val currentValue: String,
    val limitValue: String,
    val message: String,
)
