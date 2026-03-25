package com.kinetix.referencedata.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RecordBenchmarkReturnRequest(
    val returnDate: String,
    val dailyReturn: String,
)
