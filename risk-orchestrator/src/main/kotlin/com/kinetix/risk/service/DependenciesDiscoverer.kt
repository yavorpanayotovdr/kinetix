package com.kinetix.risk.service

import com.kinetix.common.model.Position
import com.kinetix.risk.client.RiskEngineClient
import com.kinetix.risk.model.DiscoveredDependency
import org.slf4j.LoggerFactory

class DependenciesDiscoverer(
    private val riskEngineClient: RiskEngineClient,
) {
    private val logger = LoggerFactory.getLogger(DependenciesDiscoverer::class.java)

    suspend fun discover(
        positions: List<Position>,
        calculationType: String,
        confidenceLevel: String,
    ): List<DiscoveredDependency> {
        val depsResponse = try {
            riskEngineClient.discoverDependencies(positions, calculationType, confidenceLevel)
        } catch (e: Exception) {
            logger.warn("Failed to discover market data dependencies, proceeding without market data", e)
            return emptyList()
        }

        val seen = mutableSetOf<Pair<String, String>>()
        return depsResponse.dependenciesList.mapNotNull { dep ->
            val dataTypeName = dep.dataType.name
            val instrumentId = dep.instrumentId
            val key = dataTypeName to instrumentId

            if (!seen.add(key)) return@mapNotNull null

            DiscoveredDependency(
                dataType = dataTypeName,
                instrumentId = instrumentId,
                assetClass = dep.assetClass,
                parameters = dep.parametersMap,
            )
        }
    }
}
