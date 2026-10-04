plugins {
    id("chattlyx.android.application")
    id("chattlyx.android.compose")
    id("chattlyx.android.hilt")
    id("chattlyx.android.test")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.chattlyx.app"

    defaultConfig {
        // AUTH-10: deletion flow also exists on the web (URL configured server-side).
        manifestPlaceholders["chattlyxDeepLinkHost"] = "chattlyx.com"
    }

    buildTypes {
        debug {
            // Debug builds are identifiable in crash logs without leaking release ids.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.analytics)
    implementation(projects.core.push)

    implementation(projects.domain)
    implementation(projects.data)

    implementation(projects.feature.onboarding)
    implementation(projects.feature.chats)
    implementation(projects.feature.calls)
    implementation(projects.feature.contacts)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.bundles.androidx.lifecycle)
}
