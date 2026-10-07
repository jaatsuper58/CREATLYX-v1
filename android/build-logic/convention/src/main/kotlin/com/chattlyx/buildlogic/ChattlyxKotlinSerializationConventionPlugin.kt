package com.chattlyx.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Applies the kotlinx.serialization compiler plugin and runtime JSON. */
class ChattlyxKotlinSerializationConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            dependencies {
                "implementation"(libs.findLibrary("kotlinx-serialization-json").orElseThrow())
            }
        }
    }
}
