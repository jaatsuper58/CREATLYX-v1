plugins {
    id("chattlyx.android.feature")
}

android {
    namespace = "com.chattlyx.feature.settings"
}

dependencies {
    // AUTH-* (Phase 8): app-lock preference + BiometricPrompt confirmation.
    implementation(projects.core.datastore)
    implementation(libs.androidx.biometric)
}
