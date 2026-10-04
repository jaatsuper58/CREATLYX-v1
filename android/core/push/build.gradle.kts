plugins {
    id("chattlyx.android.library")
}

android {
    namespace = "com.chattlyx.core.push"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    // Firebase Cloud Messaging (data-only wakeups) is added in Phase 2 with
    // the google-services integration; intentionally absent in Phase 0.
}
