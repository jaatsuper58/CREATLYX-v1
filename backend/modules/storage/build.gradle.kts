plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":modules:common"))
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
