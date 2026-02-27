package com.kinetix.risk.client

import com.kinetix.common.model.InstrumentId
import com.kinetix.common.model.VolSurface

interface VolatilityServiceClient {
    suspend fun getLatestSurface(instrumentId: InstrumentId): ClientResponse<VolSurface>
}
