package com.kinetix.gateway.client

class UpstreamErrorException(val statusCode: Int, message: String) : RuntimeException(message)
