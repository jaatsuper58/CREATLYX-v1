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
// set dynamically. AGP 9 decorated source sets break the legacy static
// interfaces, so reach it purely through the extensions container.
val androidExtension = project.extensions.getByName("android") as ExtensionAware
val androidSourceSets = androidExtension.extensions.getByName("sourceSets")
    as org.gradle.api.NamedDomainObjectContainer<*>
val mainSourceSet = androidSourceSets.getByName("main") as ExtensionAware
val protoSources = mainSourceSet.extensions.getByName("proto") as SourceDirectorySet
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
