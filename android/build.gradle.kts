// Root build file for the ChattlyX Android client.
//
// Module configuration lives in `build-logic` convention plugins
// (chattlyx.android.*, chattlyx.jvm.library). The root applies static
// analysis plugins to every project so `gradle ktlintCheck detekt` and the
// `-x ktlintCheck -x detekt` exclusions in CI always resolve.
plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
}
