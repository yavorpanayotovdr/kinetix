package com.kinetix.volatility.kafka

import com.kinetix.common.model.VolSurface

interface VolatilityPublisher {
    suspend fun publishSurface(surface: VolSurface)
}
