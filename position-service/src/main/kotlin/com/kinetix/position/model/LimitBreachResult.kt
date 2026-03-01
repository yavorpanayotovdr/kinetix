package com.kinetix.position.model

data class LimitBreachResult(
    val breaches: List<LimitBreach> = emptyList(),
) {
    val blocked: Boolean
        get() = breaches.any { it.severity == LimitBreachSeverity.HARD }
}
