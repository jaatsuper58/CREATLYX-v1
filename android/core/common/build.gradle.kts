plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.hilt")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
