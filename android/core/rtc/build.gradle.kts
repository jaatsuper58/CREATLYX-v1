plugins {
    id("chattlyx.android.library")
}

android {
    namespace = "com.chattlyx.core.rtc"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    // CALL-*: WebRTC media engine (Apache-2.0 prebuilt; Google org.webrtc API).
    // api(): the engine contract exposes org.webrtc types (EGL context,
    // SurfaceViewRenderer) to renderer-owning UI modules.
    api(libs.stream.webrtc.android)
    implementation(libs.timber)
}
