package com.kinetix.risk.model

enum class JobStepName {
    FETCH_POSITIONS,
    DISCOVER_DEPENDENCIES,
    FETCH_MARKET_DATA,
    CALCULATE_VAR,
    CALCULATE_GREEKS,
    PUBLISH_RESULT,
}
