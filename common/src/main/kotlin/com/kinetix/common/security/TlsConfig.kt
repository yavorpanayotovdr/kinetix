package com.kinetix.common.security

data class TlsConfig(
    val enabled: Boolean = false,
    val certPath: String? = null,
    val keyPath: String? = null,
    val trustStorePath: String? = null,
    val selfSigned: Boolean = false,
) {
    companion object {
        fun forTesting(): TlsConfig = TlsConfig(enabled = true, selfSigned = true)
        fun forProduction(certPath: String, keyPath: String, trustStorePath: String? = null): TlsConfig =
            TlsConfig(enabled = true, certPath = certPath, keyPath = keyPath, trustStorePath = trustStorePath)
        fun disabled(): TlsConfig = TlsConfig(enabled = false)
    }
}

data class GrpcTlsConfig(
    val tls: TlsConfig,
    val host: String = "localhost",
    val port: Int = 50051,
) {
    val isSecure: Boolean get() = tls.enabled
}
