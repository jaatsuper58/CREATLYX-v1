plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.hilt")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.crypto)
    implementation(projects.domain)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)
}
