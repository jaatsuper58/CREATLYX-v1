plugins {
    id("chattlyx.android.application")
    id("chattlyx.android.compose")
    id("chattlyx.android.hilt")
    id("chattlyx.android.test")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.chattlyx.app"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // AUTH-10: deletion flow also exists on the web (URL configured server-side).
        manifestPlaceholders["chattlyxDeepLinkHost"] = "chattlyx.com"
    }

    // Phase 8 release signing: credentials come from CI secrets / local
    // gradle.properties (never committed). Unsigned release builds stay
    // possible for local perf testing.
    val keystorePath = providers.environmentVariable("CHATTLYX_KEYSTORE_PATH")
        .orElse(providers.gradleProperty("chattlyxKeystorePath"))
    if (keystorePath.isPresent) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath.get())
                storePassword = providers.environmentVariable("CHATTLYX_KEYSTORE_PASSWORD")
                    .orElse(providers.gradleProperty("chattlyxKeystorePassword")).orNull
                keyAlias = providers.environmentVariable("CHATTLYX_KEY_ALIAS")
                    .orElse(providers.gradleProperty("chattlyxKeyAlias")).orNull
                keyPassword = providers.environmentVariable("CHATTLYX_KEY_PASSWORD")
                    .orElse(providers.gradleProperty("chattlyxKeyPassword")).orNull
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds are identifiable in crash logs without leaking release ids.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (keystorePath.isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
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
