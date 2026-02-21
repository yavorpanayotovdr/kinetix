package com.kinetix.gateway.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kinetix.common.security.Role
import java.util.Date

object TestJwtHelper {

    private const val TEST_SECRET = "test-secret-that-is-at-least-256-bits-long-for-hmac"
    private const val TEST_ISSUER = "kinetix-test"
    private const val TEST_AUDIENCE = "kinetix-api"

    fun testJwtConfig(): JwtConfig = JwtConfig(
        issuer = TEST_ISSUER,
        audience = TEST_AUDIENCE,
        realm = "kinetix-test",
        secret = TEST_SECRET,
    )

    fun generateToken(
        userId: String = "user-1",
        username: String = "testuser",
        roles: List<Role> = listOf(Role.ADMIN),
        expiresInSeconds: Long = 3600,
    ): String = JWT.create()
        .withSubject(userId)
        .withAudience(TEST_AUDIENCE)
        .withIssuer(TEST_ISSUER)
        .withClaim("preferred_username", username)
        .withClaim("roles", roles.map { it.name })
        .withExpiresAt(Date(System.currentTimeMillis() + expiresInSeconds * 1000))
        .sign(Algorithm.HMAC256(TEST_SECRET))

    fun generateExpiredToken(
        userId: String = "user-1",
        username: String = "testuser",
        roles: List<Role> = listOf(Role.ADMIN),
    ): String = JWT.create()
        .withSubject(userId)
        .withAudience(TEST_AUDIENCE)
        .withIssuer(TEST_ISSUER)
        .withClaim("preferred_username", username)
        .withClaim("roles", roles.map { it.name })
        .withExpiresAt(Date(System.currentTimeMillis() - 10_000))
        .sign(Algorithm.HMAC256(TEST_SECRET))

    fun generateTokenWithWrongSignature(
        userId: String = "user-1",
        username: String = "testuser",
        roles: List<Role> = listOf(Role.ADMIN),
    ): String = JWT.create()
        .withSubject(userId)
        .withAudience(TEST_AUDIENCE)
        .withIssuer(TEST_ISSUER)
        .withClaim("preferred_username", username)
        .withClaim("roles", roles.map { it.name })
        .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000))
        .sign(Algorithm.HMAC256("wrong-secret-that-is-also-at-least-256-bits-long"))
}
