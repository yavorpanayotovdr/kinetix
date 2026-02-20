import com.google.protobuf.gradle.id

plugins {
    id("kinetix.kotlin-common")
    id("com.google.protobuf")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "implementation"(libs.findLibrary("grpc-protobuf").get())
    "implementation"(libs.findLibrary("grpc-stub").get())
    "implementation"(libs.findLibrary("grpc-kotlin-stub").get())
    "implementation"(libs.findLibrary("protobuf-kotlin").get())
    "implementation"(libs.findLibrary("kotlinx-coroutines-core").get())
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.findVersion("protobuf").get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.findVersion("grpc").get()}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.findVersion("grpc-kotlin").get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}
