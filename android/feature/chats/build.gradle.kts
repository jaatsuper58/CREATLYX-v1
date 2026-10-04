plugins {
    id("chattlyx.android.feature")
    id("chattlyx.kotlin.serialization")
}

android {
    namespace = "com.chattlyx.feature.chats"
}

dependencies {
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
}
