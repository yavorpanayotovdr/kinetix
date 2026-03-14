package com.kinetix.risk.service

import com.kinetix.risk.model.ChangeMagnitude
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

class MarketDataQuantDiffer {

    private val json = Json { ignoreUnknownKeys = true }

    fun computeMagnitude(dataType: String, basePayload: String, targetPayload: String): ChangeMagnitude {
        return try {
            val baseObj = json.parseToJsonElement(basePayload).jsonObject
            val targetObj = json.parseToJsonElement(targetPayload).jsonObject
            when (dataType) {
                "SPOT_PRICE", "RISK_FREE_RATE", "DIVIDEND_YIELD", "CREDIT_SPREAD" ->
                    classifyScalar(baseObj, targetObj)
                "YIELD_CURVE", "FORWARD_CURVE" ->
                    classifyCurve(baseObj, targetObj)
                "VOLATILITY_SURFACE" ->
                    classifyVolSurface(baseObj, targetObj)
                "CORRELATION_MATRIX" ->
                    classifyCorrelationMatrix(baseObj, targetObj)
                "HISTORICAL_PRICES" ->
                    classifyTimeSeries(baseObj, targetObj)
                else -> ChangeMagnitude.MEDIUM
            }
        } catch (_: Exception) {
            ChangeMagnitude.MEDIUM
        }
    }

    private fun classifyScalar(base: JsonObject, target: JsonObject): ChangeMagnitude {
        val baseValue = base["value"]!!.jsonPrimitive.double
        val targetValue = target["value"]!!.jsonPrimitive.double
        val relativeChange = if (baseValue != 0.0) abs((targetValue - baseValue) / baseValue) else {
            if (targetValue != 0.0) 1.0 else 0.0
        }
        return classifyByPercentThresholds(relativeChange)
    }

    private fun classifyCurve(base: JsonObject, target: JsonObject): ChangeMagnitude {
        val basePoints = base["points"]!!.jsonArray.associate { pt ->
            pt.jsonObject["tenor"]!!.jsonPrimitive.content to pt.jsonObject["value"]!!.jsonPrimitive.double
        }
        val targetPoints = target["points"]!!.jsonArray.associate { pt ->
            pt.jsonObject["tenor"]!!.jsonPrimitive.content to pt.jsonObject["value"]!!.jsonPrimitive.double
        }
        val commonTenors = basePoints.keys.intersect(targetPoints.keys)
        if (commonTenors.isEmpty()) return ChangeMagnitude.LARGE

        val meanAbsChange = commonTenors.map { tenor ->
            val bv = basePoints[tenor]!!
            val tv = targetPoints[tenor]!!
            if (bv != 0.0) abs((tv - bv) / bv) else if (tv != 0.0) 1.0 else 0.0
        }.average()

        return classifyByPercentThresholds(meanAbsChange)
    }

    private fun classifyVolSurface(base: JsonObject, target: JsonObject): ChangeMagnitude {
        val baseColumns = base["columns"]!!.jsonArray.map { it.jsonPrimitive.content }
        val targetColumns = target["columns"]!!.jsonArray.map { it.jsonPrimitive.content }
        val baseValues = base["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        val targetValues = target["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        val baseCols = baseColumns.size
        val targetCols = targetColumns.size

        // Find ATM column index (moneyness closest to 1.00)
        val baseAtmIdx = baseColumns.indices.minByOrNull { abs(baseColumns[it].toDouble() - 1.0) } ?: 0
        val targetAtmIdx = targetColumns.indices.minByOrNull { abs(targetColumns[it].toDouble() - 1.0) } ?: 0

        // Nearest maturity = first row
        val baseAtmVol = baseValues[baseAtmIdx] // row 0, col baseAtmIdx
        val targetAtmVol = targetValues[targetAtmIdx] // row 0, col targetAtmIdx

        val relativeChange = if (baseAtmVol != 0.0) abs((targetAtmVol - baseAtmVol) / baseAtmVol) else {
            if (targetAtmVol != 0.0) 1.0 else 0.0
        }
        return classifyByPercentThresholds(relativeChange)
    }

    private fun classifyCorrelationMatrix(base: JsonObject, target: JsonObject): ChangeMagnitude {
        val baseValues = base["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        val targetValues = target["values"]!!.jsonArray.map { it.jsonPrimitive.double }
        val n = base["rows"]!!.jsonArray.size

        // Compute mean absolute off-diagonal change
        val offDiagonalChanges = mutableListOf<Double>()
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i != j) {
                    val idx = i * n + j
                    if (idx < baseValues.size && idx < targetValues.size) {
                        offDiagonalChanges.add(abs(targetValues[idx] - baseValues[idx]))
                    }
                }
            }
        }
        if (offDiagonalChanges.isEmpty()) return ChangeMagnitude.SMALL

        val meanAbsChange = offDiagonalChanges.average()
        return when {
            meanAbsChange > 0.10 -> ChangeMagnitude.LARGE
            meanAbsChange > 0.03 -> ChangeMagnitude.MEDIUM
            else -> ChangeMagnitude.SMALL
        }
    }

    private fun classifyTimeSeries(base: JsonObject, target: JsonObject): ChangeMagnitude {
        val basePoints = base["points"]!!.jsonArray
        val targetPoints = target["points"]!!.jsonArray
        if (basePoints.isEmpty() || targetPoints.isEmpty()) return ChangeMagnitude.MEDIUM

        val baseLast = basePoints.last().jsonObject["value"]!!.jsonPrimitive.double
        val targetLast = targetPoints.last().jsonObject["value"]!!.jsonPrimitive.double

        val relativeChange = if (baseLast != 0.0) abs((targetLast - baseLast) / baseLast) else {
            if (targetLast != 0.0) 1.0 else 0.0
        }
        return classifyByPercentThresholds(relativeChange)
    }

    private fun classifyByPercentThresholds(relativeChange: Double): ChangeMagnitude = when {
        relativeChange > 0.05 -> ChangeMagnitude.LARGE
        relativeChange > 0.01 -> ChangeMagnitude.MEDIUM
        else -> ChangeMagnitude.SMALL
    }
}
