plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.hilt")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}
