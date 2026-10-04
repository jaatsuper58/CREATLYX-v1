plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.hilt")
}

android {
    namespace = "com.chattlyx.core.push"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    // NOT-01: data-only FCM wakeups. Runtime needs a google-services config
    // (see docs/SECURITY.md placeholder instructions); compile-only contract.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
