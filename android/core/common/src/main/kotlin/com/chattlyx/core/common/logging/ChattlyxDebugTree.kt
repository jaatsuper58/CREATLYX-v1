package com.chattlyx.core.common.logging

import timber.log.Timber

/**
 * Debug-only Timber tree that scrubs sensitive patterns before printing.
 * Release builds plant nothing (see ChattlyxApplication).
 */
class ChattlyxDebugTree : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, SafeLog.scrub(message), t)
    }
}
