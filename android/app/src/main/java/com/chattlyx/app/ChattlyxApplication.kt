package com.chattlyx.app

import android.app.Application
import android.os.Build
import android.os.StrictMode
import com.chattlyx.core.common.logging.ChattlyxDebugTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * ChattlyX application entry point.
 *
 * Release builds use a no-op logger (Section 9.2: never log sensitive data);
 * debug builds install a scrubbing tree plus StrictMode to catch Main-thread
 * and resource violations early.
 */
@HiltAndroidApp
class ChattlyxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(ChattlyxDebugTree())
            enableStrictMode()
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        detectContentUriWithoutPermission()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        detectCredentialProtectedWhileLocked()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        detectIncorrectContextUse()
                    }
                }
                .penaltyLog()
                .build(),
        )
    }
}
