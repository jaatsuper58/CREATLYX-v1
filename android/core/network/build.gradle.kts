plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.hilt")
    id("chattlyx.android.test")
    id("chattlyx.kotlin.serialization")
}

android {
    namespace = "com.chattlyx.core.network"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.timber)

    testImplementation(libs.okhttp)
}
