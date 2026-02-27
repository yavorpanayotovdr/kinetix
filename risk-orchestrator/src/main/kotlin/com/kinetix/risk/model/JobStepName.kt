package com.kinetix.risk.model

enum class JobStepName {
    FETCH_POSITIONS,
    DISCOVER_DEPENDENCIES,
    FETCH_MARKET_DATA,
    VALUATION,
    CALCULATE_GREEKS,
    PUBLISH_RESULT,
}
