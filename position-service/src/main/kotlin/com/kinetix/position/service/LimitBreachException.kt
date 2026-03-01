package com.kinetix.position.service

import com.kinetix.position.model.LimitBreachResult

class LimitBreachException(
    val result: LimitBreachResult,
) : RuntimeException("Trade blocked by limit breach: ${result.breaches.joinToString { it.message }}")
