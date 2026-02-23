package com.kinetix.common.observability

import ch.qos.logback.classic.spi.ILoggingEvent
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

/**
 * Logback appender that auto-initializes the OpenTelemetry SDK from environment variables
 * and installs it into the OTel logback appender. When env vars are absent (e.g. in tests),
 * initialization is skipped and the appender is a silent no-op.
 *
 * SDK initialization happens in [start], but [install] is deferred to the first [append] call
 * because the appender isn't attached to loggers yet during [start], and [install] discovers
 * appenders by iterating loggers.
 */
class AutoConfigOpenTelemetryAppender : OpenTelemetryAppender() {

    override fun start() {
        if (sdk == null) {
            try {
                addInfo("Initializing OpenTelemetry SDK via autoconfigure...")
                sdk = AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk
                addInfo("OpenTelemetry SDK initialized (install deferred to first log event)")
            } catch (e: Exception) {
                addWarn("OpenTelemetry SDK auto-configuration unavailable, OTEL appender will be no-op", e)
            }
        }
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        val currentSdk = sdk
        if (currentSdk != null && !installed) {
            synchronized(lock) {
                if (!installed) {
                    install(currentSdk)
                    installed = true
                    addInfo("OpenTelemetry logback appender installed successfully")
                }
            }
        }
        super.append(event)
    }

    companion object {
        private val lock = Any()
        @Volatile
        private var sdk: OpenTelemetrySdk? = null
        @Volatile
        private var installed = false
    }
}
