package com.kinetix.risk.service

import com.kinetix.risk.model.ChangeMagnitude
import com.kinetix.risk.model.QuantDiffResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.roundToInt

class MarketDataQuantDiffer {

    private val json = Json { ignoreUnknownKeys = true }

    fun computeMagnitude(dataType: String, basePayload: String, targetPayload: String): ChangeMagnitude =
        computeQuantDiff(dataType, basePayload, targetPayload).magnitude

    fun computeQuantDiff(dataType: String, basePayload: String, targetPayload: String): QuantDiffResult {
        return try {
            val baseObj = json.parseToJsonElement(basePayload).jsonObject
            val targetObj = json.parseToJsonElement(targetPayload).jsonObject
            when (dataType) {
                "SPOT_PRICE", "RISK_FREE_RATE", "DIVIDEND_YIELD", "CREDIT_SPREAD" ->
                    classifyScalar(dataType, baseObj, targetObj)
                "YIELD_CURVE", "FORWARD_CURVE" ->
                    classifyCurve(dataType, baseObj, targetObj)
                "VOLATILITY_SURFACE" ->
                    classifyVolSurface(baseObj, targetObj)
                "CORRELATION_MATRIX" ->
                    classifyCorrelationMatrix(baseObj, targetObj)
                "HISTORICAL_PRICES" ->
                    classifyTimeSeries(baseObj, targetObj)
                else -> QuantDiffResult(ChangeMagnitude.MEDIUM)
            }
        } catch (_: Exception) {
            QuantDiffResult(ChangeMagnitude.MEDIUM, caveats = listOf("Could not parse market data payload"))
        }
    }

    private fun classifyScalar(dataType: String, base: JsonObject, target: JsonObject): QuantDiffResult {
        val baseValue = base["value"]!!.jsonPrimitive.double
        val targetValue = target["value"]!!.jsonPrimitive.double
        val relativeChange = if (baseValue != 0.0) abs((targetValue - baseValue) / baseValue) else {
            if (targetValue != 0.0) 1.0 else 0.0
        }
        val magnitude = classifyByPercentThresholds(relativeChange)
        val pctStr = formatPercent(relativeChange)
        val direction = if (targetValue >= baseValue) "up" else "down"
        val label = dataType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        val summary = "$label moved $direction $pctStr (${formatValue(baseValue)} → ${formatValue(targetValue)})"
        val caveats = buildList {
            if (baseValue == 0.0 && targetValue != 0.0) add("Base value was zero; percentage change is undefined")
        }
        return QuantDiffResult(magnitude, summary, caveats)
    }

    private fun classifyCurve(dataType: String, base: JsonObject, target: JsonObject): QuantDiffResult {
        val basePoints = base["points"]!!.jsonArray.associate { pt ->
            pt.jsonObject["tenor"]!!.jsonPrimitive.content to pt.jsonObject["value"]!!.jsonPrimitive.double
        }
        val targetPoints = target["points"]!!.jsonArray.associate { pt ->
            pt.jsonObject["tenor"]!!.jsonPrimitive.content to pt.jsonObject["value"]!!.jsonPrimitive.double
        }
        val commonTenors = basePoints.keys.intersect(targetPoints.keys)
        if (commonTenors.isEmpty()) {
            return QuantDiffResult(ChangeMagnitude.LARGE, "No common tenors between curves", listOf("Curve structure changed entirely"))
        }

        val changes = commonTenors.map { tenor ->
            val bv = basePoints[tenor]!!
            val tv = targetPoints[tenor]!!
            val relChange = if (bv != 0.0) abs((tv - bv) / bv) else if (tv != 0.0) 1.0 else 0.0
            tenor to Pair(tv - bv, relChange)
        }
        val meanAbsChange = changes.map { it.second.second }.average()
        val parallelShift = changes.map { it.second.first }.average()
        val magnitude = classifyByPercentThresholds(meanAbsChange)

        val label = if (dataType == "YIELD_CURVE") "Yield curve" else "Forward curve"
        val direction = if (parallelShift >= 0) "up" else "down"
        val summary = "$label: parallel shift $direction ${formatBps(abs(parallelShift))} avg, mean change ${formatPercent(meanAbsChange)}"

        // Slope change: longest tenor vs shortest tenor
        val caveats = buildList {
            if (commonTenors.size >= 2) {
                val sorted = commonTenors.toList().sorted()
                val shortTenor = sorted.first()
                val longTenor = sorted.last()
                val baseSlopeSpread = (basePoints[longTenor]!! - basePoints[shortTenor]!!)
                val targetSlopeSpread = (targetPoints[longTenor]!! - targetPoints[shortTenor]!!)
                val slopeChange = abs(targetSlopeSpread - baseSlopeSpread)
                if (slopeChange > abs(parallelShift) * 0.5 && slopeChange > 0.001) {
                    add("Significant slope change detected ($shortTenor to $longTenor)")
                }
            }
        }
        return QuantDiffResult(magnitude, summary, caveats)
    }

    private fun classifyVolSurface(base: JsonObject, target: JsonObject): QuantDiffResult {
        val baseColumns = base["columns"]!!.jsonArray.map { it.jsonPrimitive.content }
        val targetColumns = target["columns"]!!.jsonArray.map { it.jsonPrimitive.content }
        val baseValues = base["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        val targetValues = target["values"]!!.jsonArray.map { it.jsonPrimitive.double }

        // Find ATM column index (moneyness closest to 1.00)
        val baseAtmIdx = baseColumns.indices.minByOrNull { abs(baseColumns[it].toDouble() - 1.0) } ?: 0
        val targetAtmIdx = targetColumns.indices.minByOrNull { abs(targetColumns[it].toDouble() - 1.0) } ?: 0

        // Nearest maturity = first row
        val baseAtmVol = baseValues[baseAtmIdx] // row 0, col baseAtmIdx
        val targetAtmVol = targetValues[targetAtmIdx] // row 0, col targetAtmIdx

        val relativeChange = if (baseAtmVol != 0.0) abs((targetAtmVol - baseAtmVol) / baseAtmVol) else {
            if (targetAtmVol != 0.0) 1.0 else 0.0
        }
        val magnitude = classifyByPercentThresholds(relativeChange)

        val direction = if (targetAtmVol >= baseAtmVol) "up" else "down"
        val atmShift = targetAtmVol - baseAtmVol
        val summary = "ATM vol at nearest maturity: ${formatPercent(baseAtmVol)} → ${formatPercent(targetAtmVol)} ($direction ${formatBps(abs(atmShift))})"

        val caveats = buildList {
            // Check if skew changed while ATM stayed flat
            if (relativeChange < 0.01 && baseColumns.size >= 3 && targetColumns.size >= 3) {
                val baseSkew = baseValues.maxOrNull()!! - baseValues.minOrNull()!!
                val targetSkew = targetValues.maxOrNull()!! - targetValues.minOrNull()!!
                if (abs(targetSkew - baseSkew) > 0.02) {
                    add("ATM vol unchanged but skew shape changed; vega attribution may not be interpretable")
                }
            }
        }
        return QuantDiffResult(magnitude, summary, caveats)
    }

    private fun classifyCorrelationMatrix(base: JsonObject, target: JsonObject): QuantDiffResult {
        val baseValues = base["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        val targetValues = target["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        val rows = base["rows"]!!.jsonArray.map { it.jsonPrimitive.content }
        val n = rows.size

        // Compute mean absolute off-diagonal change and find the largest pair change
        val offDiagonalChanges = mutableListOf<Triple<Int, Int, Double>>()
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val idx = i * n + j
                if (idx < baseValues.size && idx < targetValues.size) {
                    offDiagonalChanges.add(Triple(i, j, abs(targetValues[idx] - baseValues[idx])))
                }
            }
        }
        if (offDiagonalChanges.isEmpty()) return QuantDiffResult(ChangeMagnitude.SMALL, "No off-diagonal elements to compare")

        val meanAbsChange = offDiagonalChanges.map { it.third }.average()
        val magnitude = when {
            meanAbsChange > 0.10 -> ChangeMagnitude.LARGE
            meanAbsChange > 0.03 -> ChangeMagnitude.MEDIUM
            else -> ChangeMagnitude.SMALL
        }

        val largest = offDiagonalChanges.maxByOrNull { it.third }
        val largestPairStr = if (largest != null && largest.first < rows.size && largest.second < rows.size) {
            "${rows[largest.first]}/${rows[largest.second]}"
        } else null

        val summary = buildString {
            append("Mean abs off-diagonal change: ${String.format("%.4f", meanAbsChange)}")
            if (largestPairStr != null && largest != null) {
                append("; largest pair: $largestPairStr (${String.format("%.4f", largest.third)})")
            }
        }

        val caveats = buildList {
            // Spectral norm approximation: if mean change is large, warn
            if (meanAbsChange > 0.15) {
                add("Correlation regime change detected; attribution is approximate")
            }
        }
        return QuantDiffResult(magnitude, summary, caveats)
    }

    private fun classifyTimeSeries(base: JsonObject, target: JsonObject): QuantDiffResult {
        val basePoints = base["points"]!!.jsonArray
        val targetPoints = target["points"]!!.jsonArray
        if (basePoints.isEmpty() || targetPoints.isEmpty()) {
            return QuantDiffResult(ChangeMagnitude.MEDIUM, caveats = listOf("Empty time series data"))
        }

        val baseLast = basePoints.last().jsonObject["value"]!!.jsonPrimitive.double
        val targetLast = targetPoints.last().jsonObject["value"]!!.jsonPrimitive.double

        val relativeChange = if (baseLast != 0.0) abs((targetLast - baseLast) / baseLast) else {
            if (targetLast != 0.0) 1.0 else 0.0
        }
        val magnitude = classifyByPercentThresholds(relativeChange)

        // Compute annualized vol change if we have enough points
        val baseVol = computeAnnualizedVol(basePoints.map { it.jsonObject["value"]!!.jsonPrimitive.double })
        val targetVol = computeAnnualizedVol(targetPoints.map { it.jsonObject["value"]!!.jsonPrimitive.double })

        val summary = buildString {
            val direction = if (targetLast >= baseLast) "up" else "down"
            append("Latest value $direction ${formatPercent(relativeChange)}")
            if (baseVol != null && targetVol != null) {
                append("; annualized vol: ${formatPercent(baseVol)} → ${formatPercent(targetVol)}")
            }
        }

        return QuantDiffResult(magnitude, summary)
    }

    private fun computeAnnualizedVol(values: List<Double>): Double? {
        if (values.size < 3) return null
        val returns = values.zipWithNext().mapNotNull { (prev, curr) ->
            if (prev != 0.0) (curr - prev) / prev else null
        }
        if (returns.isEmpty()) return null
        val mean = returns.average()
        val variance = returns.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance * 252)
    }

    private fun classifyByPercentThresholds(relativeChange: Double): ChangeMagnitude = when {
        relativeChange > 0.05 -> ChangeMagnitude.LARGE
        relativeChange > 0.01 -> ChangeMagnitude.MEDIUM
        else -> ChangeMagnitude.SMALL
    }

    private fun formatPercent(value: Double): String =
        "${(value * 100).let { String.format("%.2f", it) }}%"

    private fun formatBps(value: Double): String =
        "${(value * 10000).roundToInt()}bps"

    private fun formatValue(value: Double): String =
        String.format("%.4f", value)
}
