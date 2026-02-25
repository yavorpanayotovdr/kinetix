package com.kinetix.risk.service

import com.kinetix.common.model.Position
import com.kinetix.risk.model.DiscoveredDependency

class PositionDependencyGrouper {

    fun group(
        positions: List<Position>,
        dependencies: List<DiscoveredDependency>,
    ): Map<String, List<DiscoveredDependency>> {
        if (positions.isEmpty() || dependencies.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, MutableList<DiscoveredDependency>>()

        for (dep in dependencies) {
            when {
                dep.instrumentId.isNotEmpty() -> {
                    val matching = positions.find { it.instrumentId.value == dep.instrumentId }
                    if (matching != null) {
                        result.getOrPut(matching.instrumentId.value) { mutableListOf() }.add(dep)
                    }
                }
                dep.assetClass.isNotEmpty() -> {
                    positions.filter { it.assetClass.name == dep.assetClass }.forEach { pos ->
                        result.getOrPut(pos.instrumentId.value) { mutableListOf() }.add(dep)
                    }
                }
                else -> {
                    positions.forEach { pos ->
                        result.getOrPut(pos.instrumentId.value) { mutableListOf() }.add(dep)
                    }
                }
            }
        }

        return result
    }
}
