plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
    implementation("io.ktor.plugin:plugin:${libs.versions.ktor.get()}")
    implementation("com.google.protobuf:protobuf-gradle-plugin:${libs.versions.protobuf.plugin.get()}")
}
