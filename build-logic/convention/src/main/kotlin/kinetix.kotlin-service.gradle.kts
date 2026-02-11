plugins {
    id("kinetix.kotlin-common")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(libs.findBundle("ktor-server").get())
    "implementation"(libs.findLibrary("logback-classic").get())

    "testImplementation"(libs.findLibrary("ktor-server-test-host").get())
}
