plugins {
    id("chattlyx.android.library")
}

android {
    namespace = "com.chattlyx.core.rtc"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
}
