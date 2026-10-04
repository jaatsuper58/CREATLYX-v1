package com.chattlyx.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature module bundle: library + Compose + Hilt + tests, plus the shared
 * core modules every screen needs (designsystem, ui, common, domain).
 */
class ChattlyxFeatureConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(ChattlyxAndroidLibraryConventionPlugin::class.java)
            pluginManager.apply(ChattlyxComposeConventionPlugin::class.java)
            pluginManager.apply(ChattlyxHiltConventionPlugin::class.java)
            pluginManager.apply(ChattlyxAndroidTestConventionPlugin::class.java)

            dependencies {
                "implementation"(project(":core:common"))
                "implementation"(project(":core:designsystem"))
                "implementation"(project(":core:ui"))
                "implementation"(project(":core:network"))
                "implementation"(project(":domain"))

                libs.findLibrary("androidx-hilt-navigation-compose").ifPresent { "implementation"(it) }
                libs.findLibrary("androidx-lifecycle-runtime-compose").ifPresent { "implementation"(it) }
                libs.findLibrary("androidx-lifecycle-viewmodel-compose").ifPresent { "implementation"(it) }
                libs.findLibrary("androidx-navigation-compose").ifPresent { "implementation"(it) }
                libs.findLibrary("kotlinx-serialization-json").ifPresent { "implementation"(it) }
                libs.findLibrary("timber").ifPresent { "implementation"(it) }
            }
        }
    }
}
