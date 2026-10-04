pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "chattlyx-backend"

include(":server")
include(":modules:common")
include(":modules:db")
include(":modules:auth")
include(":modules:protocol")
include(":modules:redis")
include(":modules:messaging")
