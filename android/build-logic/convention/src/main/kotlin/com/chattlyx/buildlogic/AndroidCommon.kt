package com.chattlyx.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * AGP 9 DSL: block methods (defaultConfig, compileOptions, testOptions,
 * packaging) moved from CommonExtension onto the concrete extension types,
 * and the legacy `kotlinOptions` bridge is gone — Kotlin settings go through
 * [KotlinAndroidProjectExtension.compilerOptions].
 */
internal fun LibraryExtension.configureDefaults(project: Project) {
    compileSdk = ChattlyxBuild.COMPILE_SDK
    defaultConfig {
        minSdk = ChattlyxBuild.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = ChattlyxBuild.JAVA_VERSION
        targetCompatibility = ChattlyxBuild.JAVA_VERSION
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // Phase 7 hardening turns this back on; lint currently flags legacy
    // patterns across modules and must not gate the Phase 2/3 build.
    lint {
        abortOnError = false
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
        )
    }
    project.configureKotlinJvmTarget()
}

internal fun ApplicationExtension.configureDefaults(project: Project) {
    compileSdk = ChattlyxBuild.COMPILE_SDK
    defaultConfig {
        minSdk = ChattlyxBuild.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = ChattlyxBuild.JAVA_VERSION
        targetCompatibility = ChattlyxBuild.JAVA_VERSION
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    // Phase 7 hardening turns this back on; lint currently flags legacy
    // patterns across modules and must not gate the Phase 2/3 build.
    lint {
        abortOnError = false
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
        )
    }
    project.configureKotlinJvmTarget()
}

private fun Project.configureKotlinJvmTarget() {
    extensions.getByType<KotlinAndroidProjectExtension>().compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(ChattlyxBuild.JVM_TARGET))
    }
}

internal fun Project.addCoreDependency(artifact: String) {
    dependencies.add("implementation", libs.findLibrary(artifact).orElseThrow {
        IllegalStateException("Missing catalog entry: $artifact")
    })
}
