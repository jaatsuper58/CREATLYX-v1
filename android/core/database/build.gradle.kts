plugins {
    id("chattlyx.android.library")
    id("chattlyx.android.hilt")
    id("chattlyx.android.test")
}

android {
    namespace = "com.chattlyx.core.database"
}

// Room schema exports are committed for migration testing (Section 7.1).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.crypto)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.common)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.core)
}
