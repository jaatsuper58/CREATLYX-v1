package com.chattlyx.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose UI: compiler plugin (Kotlin 2.x bundles it via
 * org.jetbrains.kotlin.plugin.compose), BOM, M3, tooling and ui-test deps.
 */
class ChattlyxComposeConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<CommonExtension<*, *, *, *, *, *>> {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = libs.findLibrary("compose-bom").orElseThrow()
                "implementation"(platform(bom))
                "androidTestImplementation"(platform(bom))

                listOf(
                    "compose-ui",
                    "compose-ui-graphics",
                    "compose-ui-tooling-preview",
                    "compose-foundation",
                    "compose-material3",
                    "compose-material-icons-core",
                    "androidx-activity-compose",
                ).forEach { name ->
                    libs.findLibrary(name).ifPresent { "implementation"(it) }
                }
                libs.findLibrary("compose-ui-tooling").ifPresent { "debugImplementation"(it) }
                libs.findLibrary("compose-ui-test-junit4").ifPresent { "androidTestImplementation"(it) }
                libs.findLibrary("compose-ui-test-manifest").ifPresent { "debugImplementation"(it) }
            }
        }
    }
}
