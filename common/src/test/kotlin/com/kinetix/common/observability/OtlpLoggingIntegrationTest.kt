package com.kinetix.common.observability

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import org.slf4j.LoggerFactory

class OtlpLoggingIntegrationTest : FunSpec({

    val exporter = InMemoryLogRecordExporter.create()

    val sdk = OpenTelemetrySdk.builder()
        .setLoggerProvider(
            SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                .build()
        )
        .build()

    beforeSpec {
        OpenTelemetryAppender.install(sdk)
    }

    afterSpec {
        sdk.close()
    }

    beforeEach {
        exporter.reset()
    }

    test("SLF4J log records are exported through the OpenTelemetry pipeline") {
        val logger = LoggerFactory.getLogger("com.kinetix.test.OtlpPipeline")

        logger.info("Info message for OTLP test")
        logger.warn("Warning message for OTLP test")
        logger.error("Error message for OTLP test")

        val records = exporter.finishedLogRecordItems
        records shouldHaveSize 3

        val info = records[0]
        info.bodyValue!!.asString() shouldBe "Info message for OTLP test"
        info.severity.name shouldContain "INFO"

        val warn = records[1]
        warn.bodyValue!!.asString() shouldBe "Warning message for OTLP test"
        warn.severity.name shouldContain "WARN"

        val error = records[2]
        error.bodyValue!!.asString() shouldBe "Error message for OTLP test"
        error.severity.name shouldContain "ERROR"
    }

    test("exported log records contain the logger name") {
        val loggerName = "com.kinetix.test.LoggerNameCheck"
        val logger = LoggerFactory.getLogger(loggerName)

        logger.info("Checking logger name attribute")

        val records = exporter.finishedLogRecordItems
        records shouldHaveSize 1
        records[0].instrumentationScopeInfo.name shouldBe loggerName
    }
})
