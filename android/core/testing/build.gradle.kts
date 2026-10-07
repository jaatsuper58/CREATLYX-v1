plugins {
    id("chattlyx.android.library")
}

android {
    namespace = "com.chattlyx.core.testing"
}

dependencies {
    api(libs.bundles.unit.testing)
    implementation(libs.kotlinx.coroutines.core)
}
