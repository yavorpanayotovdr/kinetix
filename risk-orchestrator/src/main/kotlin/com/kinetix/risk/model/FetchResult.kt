package com.kinetix.risk.model

sealed interface FetchResult {
    val dependency: DiscoveredDependency
}
