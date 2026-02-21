plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("io.gatling.gradle") version "3.13.4"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}
