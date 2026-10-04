// Root build file for the ChattlyX Android client.
//
// Module configuration lives in `build-logic` convention plugins
// (chattlyx.android.*, chattlyx.jvm.library).
//
// Static analysis:
//  - ktlint-gradle 14.2.0 works with AGP 9 and is applied to every project.
//  - detekt 1.23.8's DetektAndroid still references com.android.build.gradle
//    .BaseExtension, which AGP 9 removed, so detekt runs only on the root
//    (no sources today). Re-enable per-module detekt once detekt 2.x stable
//    supports AGP 9 (tracked as Phase 2 tech debt).
plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
