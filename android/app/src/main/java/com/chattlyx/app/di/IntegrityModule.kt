package com.chattlyx.app.di

import android.content.Context
import com.chattlyx.app.BuildConfig
import com.chattlyx.app.integrity.DeviceIntegrityProvider
import com.chattlyx.app.integrity.createDeviceIntegrityProvider
import com.chattlyx.core.common.dispatchers.ChattlyxDispatcher
import com.chattlyx.core.common.dispatchers.Dispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/**
 * SAF hardening (Phase 7): Play Integrity binding. Off unless the build
 * carries a linked GCP project number (see docs/SECURITY.md, "Play
 * Integrity rollout"); unconfigured builds get the noop provider so no
 * Play round-trips happen in dev/CI.
 */
@Module
@InstallIn(SingletonComponent::class)
object IntegrityModule {

    @Provides
    @Singleton
    fun provideDeviceIntegrityProvider(
        @ApplicationContext context: Context,
        @Dispatcher(ChattlyxDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DeviceIntegrityProvider = createDeviceIntegrityProvider(
        context = context,
        enabled = BuildConfig.INTEGRITY_ENABLED,
        cloudProjectNumber = BuildConfig.INTEGRITY_CLOUD_PROJECT_NUMBER,
        ioDispatcher = ioDispatcher,
    )
}
