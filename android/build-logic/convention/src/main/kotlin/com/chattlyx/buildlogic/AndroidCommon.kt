package com.chattlyx.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun CommonExtension<*, *, *, *, *, *>.configureDefaults() {
    compileSdk = ChattlyxBuild.COMPILE_SDK
    defaultConfig {
        minSdk = ChattlyxBuild.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = ChattlyxBuild.JAVA_VERSION
        targetCompatibility = ChattlyxBuild.JAVA_VERSION
    }
    (this as ExtensionAware).extensions.getByType<KotlinJvmOptions>().apply {
        jvmTarget = ChattlyxBuild.JVM_TARGET
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
        )
    }
}

internal fun Project.addCoreDependency(artifact: String) {
    dependencies {
        "implementation"(libs.findLibrary(artifact).orElseThrow {
            IllegalStateException("Missing catalog entry: $artifact")
        })
    }
}
