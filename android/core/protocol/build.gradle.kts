plugins {
    id("chattlyx.android.library")
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.chattlyx.core.protocol"

    // Wire contract classes (javalite) generated from the shared /proto tree.
    sourceSets {
        main {
            proto {
                srcDir("../../../proto")
            }
        }
    }
}

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
