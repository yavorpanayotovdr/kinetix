package com.kinetix.risk.model

enum class BreachStatus {
    /** VaR utilisation is below the warning threshold. */
    WITHIN_BUDGET,

    /** VaR utilisation has reached or exceeded the warning threshold (default: 80%). */
    WARNING,

    /** VaR utilisation has reached or exceeded the budget amount. */
    BREACH,
}
