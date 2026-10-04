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
    implementation(projects.core.protocol)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Retrofit types (Response<T>) and okhttp appear in this module's public
    // API surface, so consumers need them on their compile classpath.
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.timber)

    testImplementation(libs.okhttp)
}
