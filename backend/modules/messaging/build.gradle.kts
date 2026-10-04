plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":modules:common"))
    api(project(":modules:protocol"))
    implementation(project(":modules:db"))
    implementation(project(":modules:redis"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    useJUnitPlatform()
}
