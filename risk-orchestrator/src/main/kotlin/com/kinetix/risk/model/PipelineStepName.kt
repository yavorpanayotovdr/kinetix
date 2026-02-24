package com.kinetix.risk.model

enum class PipelineStepName {
    FETCH_POSITIONS,
    DISCOVER_DEPENDENCIES,
    FETCH_MARKET_DATA,
    CALCULATE_VAR,
    PUBLISH_RESULT,
}
