package com.kinetix.risk.service

import com.kinetix.risk.client.ClientResponse

/**
 * Thrown internally by [MarketDataFetcher] when a circuit breaker is active and the underlying
 * fetch returned an error response variant. This allows the circuit breaker to record the failure
 * before the response is converted back into a [com.kinetix.risk.model.FetchFailure].
 */
internal class MarketDataFetchException(
    val dataType: String,
    val response: ClientResponse<Nothing>,
) : RuntimeException("Market data fetch failed for $dataType: $response")
