package com.chattlyx.app.di

import com.chattlyx.app.telecom.TelecomCallNotifier
import com.chattlyx.domain.calls.CallTelecomNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** CALL-06 (Phase 8): wires the system-telecom notifier into the call stack. */
@Module
@InstallIn(SingletonComponent::class)
interface TelecomModule {

    @Binds
    fun bindCallTelecomNotifier(impl: TelecomCallNotifier): CallTelecomNotifier
}
