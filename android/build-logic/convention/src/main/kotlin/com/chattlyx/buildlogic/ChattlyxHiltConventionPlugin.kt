package com.chattlyx.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt DI: plugin + annotation processor via KSP. */
class ChattlyxHiltConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                "implementation"(libs.findLibrary("hilt-android").orElseThrow())
                "ksp"(libs.findLibrary("hilt-compiler").orElseThrow())
            }

            // Dagger 2.60 still bundles kotlin-metadata-jvm 2.3.x, which rejects
            // Kotlin 2.4 class metadata (google/dagger#5177). Force the reader
            // that matches our compiler until Dagger catches up.
            val kotlinVersion = libs.findVersion("kotlin").orElseThrow().requiredVersion
            configurations.configureEach {
                resolutionStrategy {
                    force("org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinVersion")
                }
            }
        }
    }
}
