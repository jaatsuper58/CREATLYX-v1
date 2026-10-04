plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.core.crypto"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)
}
