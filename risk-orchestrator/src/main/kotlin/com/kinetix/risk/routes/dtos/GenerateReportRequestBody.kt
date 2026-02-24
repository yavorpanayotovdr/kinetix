package com.kinetix.risk.routes.dtos

import kotlinx.serialization.Serializable

@Serializable
data class GenerateReportRequestBody(val format: String? = null)
