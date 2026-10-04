plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.compose")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
