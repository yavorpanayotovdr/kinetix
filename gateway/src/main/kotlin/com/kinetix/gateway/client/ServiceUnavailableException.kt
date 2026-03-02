package com.kinetix.gateway.client

class ServiceUnavailableException(val retryAfterSeconds: Int?, message: String) : RuntimeException(message)
