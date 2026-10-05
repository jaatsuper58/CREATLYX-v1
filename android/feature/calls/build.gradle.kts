plugins {
    id("chattlyx.android.feature")
}

android {
    namespace = "com.chattlyx.feature.calls"
}

dependencies {
    // CALL-01 video surface: the renderer type + EGL context live in core:rtc.
    implementation(projects.core.rtc)
}
