package com.chattlyx.buildlogic

/** Single source for Android SDK levels across every module. */
internal object ChattlyxBuild {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 24
<<<<<<< HEAD
    const val VERSION_CODE = 9
    const val VERSION_NAME = "0.9.0-beta"
=======
    const val VERSION_CODE = 10
    const val VERSION_NAME = "0.10.0-beta"
>>>>>>> 21caadb (feat(CALL-06): Phase 8 — self-managed ConnectionService telecom integration (PhoneAccount, system answer/reject/hang-up delegation, CallTelecomNotifier port), 0.10.0-beta)
    const val APPLICATION_ID = "com.chattlyx.app"

    const val JVM_TARGET = "17"
    val JAVA_VERSION = org.gradle.api.JavaVersion.VERSION_17
}
