plugins {
    id("chattlyx.android.library")
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.chattlyx.core.protocol"
}

// Wire contract classes (javalite) generated from the shared /proto tree.
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

// protobuf 0.10: the Android proto entry is a SourceDirectorySet property.
android.sourceSets.getByName("main").proto.srcDir("../../../proto")

dependencies {
    api(libs.protobuf.javalite)
}
