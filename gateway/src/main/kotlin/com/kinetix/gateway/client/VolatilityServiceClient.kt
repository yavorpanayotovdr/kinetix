package com.kinetix.gateway.client

import com.kinetix.gateway.dto.VolSurfaceDiffResponse
import com.kinetix.gateway.dto.VolSurfaceResponse

interface VolatilityServiceClient {
    suspend fun getSurface(instrumentId: String): VolSurfaceResponse?
    suspend fun getSurfaceDiff(instrumentId: String, compareDate: String): VolSurfaceDiffResponse?
}
