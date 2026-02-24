package com.kinetix.price.kafka

import com.kinetix.common.model.PricePoint

interface PricePublisher {
    suspend fun publish(point: PricePoint)
}
