package com.chattlyx.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * AGP 9 DSL: [CommonExtension] is no longer generic and the legacy
 * `kotlinOptions` bridge is gone — Kotlin settings go through
 * [KotlinAndroidProjectExtension.compilerOptions] (KGP 2.x).
 */
internal fun CommonExtension.configureDefaults(project: Project) {
    compileSdk = ChattlyxBuild.COMPILE_SDK
    defaultConfig {
        minSdk = ChattlyxBuild.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = ChattlyxBuild.JAVA_VERSION
        targetCompatibility = ChattlyxBuild.JAVA_VERSION
    }
    project.extensions.getByType<KotlinAndroidProjectExtension>().compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(ChattlyxBuild.JVM_TARGET))
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
    dependencies.add("implementation", libs.findLibrary(artifact).orElseThrow {
        IllegalStateException("Missing catalog entry: $artifact")
    })
}
