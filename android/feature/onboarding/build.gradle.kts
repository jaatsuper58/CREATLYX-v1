plugins {
    id("chattlyx.android.feature")
}

android {
    namespace = "com.chattlyx.feature.onboarding"
}

dependencies {
    // SMS Retriever auto-read (AUTH-03); optional at runtime — the feature
    // degrades to manual entry on non-GMS devices.
    implementation(libs.play.services.auth.api.phone)
}
