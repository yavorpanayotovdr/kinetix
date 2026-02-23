plugins {
    id("kinetix.kotlin-library")
    id("kinetix.kotlin-testing")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    compileOnly(libs.logback.classic)
    compileOnly(libs.opentelemetry.sdk.autoconfigure)
    compileOnly(libs.opentelemetry.logback.appender)
}
