package com.kinetix.gateway.auth

data class JwtConfig(
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String? = null,
    val jwksUrl: String? = null,
)
