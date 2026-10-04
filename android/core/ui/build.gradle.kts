plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.compose")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
}
