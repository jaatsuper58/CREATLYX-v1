plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
}

// Generated Java classes for the shared wire contract in /proto.
// Source of truth stays outside the backend tree (single copy, both sides).
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.3" // verify latest stable
    }
}

sourceSets {
    main {
        proto {
            srcDir("../../../proto")
        }
    }
}

dependencies {
    api(libs.protobuf.java)
}
