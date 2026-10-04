import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware

plugins {
    id("chattlyx.android.library")
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.chattlyx.core.protocol"
}

// protobuf 0.10 adds the "proto" SourceDirectorySet to each Android source
// set dynamically (no Kotlin DSL accessor). Under android.newDsl=false the
// extension is the legacy BaseExtension; its sourceSets are a plain getter.
@Suppress("DEPRECATION")
val legacyAndroid = project.extensions.getByName("android") as com.android.build.gradle.BaseExtension
val protoSources = (legacyAndroid.sourceSets.getByName("main") as ExtensionAware)
    .extensions.getByName("proto") as SourceDirectorySet
// Wire contract sources live in the shared /proto tree (single copy, both sides).
protoSources.srcDir("../../../proto")

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3" // verify latest stable
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("javalite")
            }
        }
    }
}

dependencies {
    api(libs.protobuf.javalite)
}
