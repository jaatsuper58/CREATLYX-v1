plugins {
    id("chattlyx.android.library")
}

android {
    namespace = "com.chattlyx.core.work"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    // WorkManager dependency lands in Phase 1 with the first scheduled jobs
    // (prekey replenishment, contact discovery, periodic sync).
}
