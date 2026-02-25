plugins {
    id("kinetix.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(libs.findBundle("ktor-server").get())
    "implementation"(libs.findLibrary("ktor-openapi").get())
    "implementation"(libs.findLibrary("ktor-swagger-ui").get())
    "implementation"(libs.findLibrary("logback-classic").get())
    "implementation"(libs.findBundle("opentelemetry-logging").get())

    "testImplementation"(libs.findLibrary("ktor-server-test-host").get())
}

application {
    applicationDefaultJvmArgs = listOf(
        "-Xmx512m",
        "-XX:+UseG1GC",
        "-XX:+TieredCompilation",
    )
}

tasks.named<JavaExec>("run") {
    environment("OTEL_SERVICE_NAME", project.name)
    environment("KINETIX_DEV_MODE", System.getenv("KINETIX_DEV_MODE") ?: "false")
}
