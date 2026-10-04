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

    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
}
