plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":modules:common"))
    implementation(libs.kotlinx.coroutines.core)
    api(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
