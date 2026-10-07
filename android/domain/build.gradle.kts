plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.domain"
}

dependencies {
    api(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    implementation(libs.androidx.paging.common)
}
