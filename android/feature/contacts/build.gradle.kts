plugins {
    id("chattlyx.android.feature")
    id("chattlyx.kotlin.serialization")
}

android {
    namespace = "com.chattlyx.feature.contacts"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
