pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "chattlyx-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:ui")
include(":core:network")
include(":core:protocol")
include(":core:database")
include(":core:datastore")
include(":core:crypto")
include(":core:rtc")
include(":core:push")
include(":core:work")
include(":core:analytics")
include(":core:testing")
include(":domain")
include(":data")
include(":feature:onboarding")
include(":feature:chats")
include(":feature:chat")
include(":feature:groups")
include(":feature:contacts")
include(":feature:calls")
include(":feature:media")
include(":feature:search")
include(":feature:settings")
include(":feature:backup")
include(":feature:status")
// Macrobenchmark needs a physical device/emulator to be meaningful, so CI
// skips it (it runs in the Phase 7 device lab). Build it locally with
// -PchattlyxBenchmark=true.
if (providers.gradleProperty("chattlyxBenchmark").getOrElse("false") == "true") {
    include(":benchmark")
}
