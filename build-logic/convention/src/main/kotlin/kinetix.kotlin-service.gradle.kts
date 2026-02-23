plugins {
    id("kinetix.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(libs.findBundle("ktor-server").get())
    "implementation"(libs.findLibrary("logback-classic").get())
    "implementation"(libs.findBundle("opentelemetry-logging").get())

    "testImplementation"(libs.findLibrary("ktor-server-test-host").get())
}

tasks.named<JavaExec>("run") {
    environment("OTEL_SERVICE_NAME", project.name)
}
