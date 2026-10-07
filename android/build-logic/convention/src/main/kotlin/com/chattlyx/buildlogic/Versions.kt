package com.chattlyx.buildlogic

/** Single source for Android SDK levels across every module. */
internal object ChattlyxBuild {
    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 24
    const val VERSION_CODE = 11
    const val VERSION_NAME = "0.11.0-beta"
    const val APPLICATION_ID = "com.chattlyx.app"

    const val JVM_TARGET = "17"
    val JAVA_VERSION = org.gradle.api.JavaVersion.VERSION_17
}
