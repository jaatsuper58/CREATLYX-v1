plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    group = "com.chattlyx.backend"
    version = "0.1.0"
}

subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("failed")
            setExceptionFormat(org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL)
            showStackTraces = true
            showStandardStreams = true
        }
    }
}
