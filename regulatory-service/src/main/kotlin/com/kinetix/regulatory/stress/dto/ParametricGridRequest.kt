package com.kinetix.regulatory.stress.dto

import kotlinx.serialization.Serializable

@Serializable
data class ParametricGridRequest(
    val bookId: String,
    val primaryAxis: String,
    val primaryRange: List<Double>,
    val secondaryAxis: String,
    val secondaryRange: List<Double>,
)
