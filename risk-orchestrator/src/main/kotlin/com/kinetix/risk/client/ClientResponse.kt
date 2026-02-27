package com.kinetix.risk.client

sealed interface ClientResponse<out T> {
    data class Success<T>(val value: T) : ClientResponse<T>
    data class NotFound(val httpStatus: Int) : ClientResponse<Nothing>
}
