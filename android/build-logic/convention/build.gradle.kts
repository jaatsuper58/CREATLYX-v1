plugins {
    `kotlin-dsl`
}

group = "com.chattlyx.build"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("chattlyxAndroidApplication") {
            id = "chattlyx.android.application"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxAndroidApplicationConventionPlugin"
        }
        register("chattlyxAndroidLibrary") {
            id = "chattlyx.android.library"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxAndroidLibraryConventionPlugin"
        }
        register("chattlyxAndroidCompose") {
            id = "chattlyx.android.compose"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxComposeConventionPlugin"
        }
        register("chattlyxAndroidHilt") {
            id = "chattlyx.android.hilt"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxHiltConventionPlugin"
        }
        register("chattlyxAndroidTest") {
            id = "chattlyx.android.test"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxAndroidTestConventionPlugin"
        }
        register("chattlyxAndroidFeature") {
            id = "chattlyx.android.feature"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxFeatureConventionPlugin"
        }
        register("chattlyxKotlinSerialization") {
            id = "chattlyx.kotlin.serialization"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxKotlinSerializationConventionPlugin"
        }
        register("chattlyxJvmLibrary") {
            id = "chattlyx.jvm.library"
            implementationClass = "com.chattlyx.buildlogic.ChattlyxJvmLibraryConventionPlugin"
        }
    }
}
