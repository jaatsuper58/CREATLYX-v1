plugins {
    id("chattlyx.android.library")
}

android {
    namespace = "com.chattlyx.core.analytics"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
