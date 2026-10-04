plugins {
    id("chattlyx.jvm.library")
    id("chattlyx.android.test")
}

dependencies {
    api(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
