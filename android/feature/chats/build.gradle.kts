plugins {
    id("chattlyx.android.feature")
    id("chattlyx.kotlin.serialization")
}

android {
    namespace = "com.chattlyx.feature.chats"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    // MED-* media rendering + pickers + voice playback/recording.
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.activity.compose)
}
