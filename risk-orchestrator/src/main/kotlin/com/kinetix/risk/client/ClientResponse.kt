package com.kinetix.risk.client

sealed interface ClientResponse<out T> {
    data class Success<T>(val value: T) : ClientResponse<T>
    data class NotFound(val httpStatus: Int) : ClientResponse<Nothing>
    data class ServiceUnavailable(val retryAfterSeconds: Int? = null) : ClientResponse<Nothing>
    data class UpstreamError(val httpStatus: Int, val message: String) : ClientResponse<Nothing>
    data class NetworkError(val cause: Throwable) : ClientResponse<Nothing>
}
