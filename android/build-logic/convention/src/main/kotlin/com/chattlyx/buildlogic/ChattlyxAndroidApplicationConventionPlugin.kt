package com.chattlyx.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** App-shell configuration: ids, versions, debug/release build types. */
class ChattlyxAndroidApplicationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<ApplicationExtension> {
                configureDefaults()

                defaultConfig {
                    applicationId = ChattlyxBuild.APPLICATION_ID
                    targetSdk = ChattlyxBuild.TARGET_SDK
                    versionCode = ChattlyxBuild.VERSION_CODE
                    versionName = ChattlyxBuild.VERSION_NAME
                }

                buildTypes {
                    debug {
                        isMinifyEnabled = false
                        applicationIdSuffix = ".dev"
                    }
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
            }
        }
    }
}
