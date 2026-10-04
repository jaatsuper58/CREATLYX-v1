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
                // Scaffolded feature modules carry test sources but no tests
                // yet (Phases 4-6); do not fail the build on them.
                failOnNoDiscoveredTests = false
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
                // Robolectric screenshot tests are JUnit 4 style; run them on the
                // JUnit 5 platform via the vintage engine.
                listOf("junit4", "robolectric").forEach { name ->
                    libs.findLibrary(name).ifPresent { "testImplementation"(it) }
                }
                libs.findLibrary("junit-vintage-engine").ifPresent { "testRuntimeOnly"(it) }
                libs.findLibrary("kotlin-test").ifPresent { "testImplementation"(it) }
            }
        }
    }
}
