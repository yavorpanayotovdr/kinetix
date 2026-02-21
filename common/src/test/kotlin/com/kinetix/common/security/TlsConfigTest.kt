package com.kinetix.common.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TlsConfigTest : FunSpec({

    test("TlsConfig has required fields") {
        val config = TlsConfig(
            enabled = true,
            certPath = "/certs/cert.pem",
            keyPath = "/certs/key.pem",
            trustStorePath = "/certs/ca.pem",
            selfSigned = false,
        )
        config.enabled shouldBe true
        config.certPath shouldBe "/certs/cert.pem"
        config.keyPath shouldBe "/certs/key.pem"
        config.trustStorePath shouldBe "/certs/ca.pem"
        config.selfSigned shouldBe false
    }

    test("TlsConfig defaults to disabled") {
        val config = TlsConfig()
        config.enabled shouldBe false
        config.certPath shouldBe null
        config.keyPath shouldBe null
        config.trustStorePath shouldBe null
        config.selfSigned shouldBe false
    }

    test("TlsConfig.forTesting creates config with self-signed enabled") {
        val config = TlsConfig.forTesting()
        config.enabled shouldBe true
        config.selfSigned shouldBe true
    }

    test("TlsConfig.forProduction requires cert and key paths") {
        val config = TlsConfig.forProduction("/certs/cert.pem", "/certs/key.pem", "/certs/ca.pem")
        config.enabled shouldBe true
        config.certPath shouldBe "/certs/cert.pem"
        config.keyPath shouldBe "/certs/key.pem"
        config.trustStorePath shouldBe "/certs/ca.pem"
        config.selfSigned shouldBe false
    }

    test("GrpcTlsConfig wraps TlsConfig for gRPC channel") {
        val tls = TlsConfig.forTesting()
        val grpcConfig = GrpcTlsConfig(tls = tls, host = "risk-engine", port = 50051)
        grpcConfig.isSecure shouldBe true
        grpcConfig.host shouldBe "risk-engine"
        grpcConfig.port shouldBe 50051

        val insecure = GrpcTlsConfig(tls = TlsConfig.disabled())
        insecure.isSecure shouldBe false
    }
})
