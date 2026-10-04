package com.chattlyx.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** JUnit 5 unit tests (Robolectric where SDK types are needed). */
class ChattlyxAndroidTestConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
                testLogging {
                    events("passed", "failed", "skipped")
                }
            }

            dependencies {
                listOf(
                    "junit-jupiter",
                    "junit-jupiter-params",
                ).forEach { name ->
                    libs.findLibrary(name).ifPresent { "testImplementation"(it) }
                }
                listOf("mockk", "turbine", "kotlinx-coroutines-test").forEach { name ->
                    libs.findLibrary(name).ifPresent { "testImplementation"(it) }
                }
                libs.findLibrary("kotlin-test").ifPresent { "testImplementation"(it) }
            }
        }
    }
}
