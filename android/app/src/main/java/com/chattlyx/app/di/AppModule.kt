package com.chattlyx.app.di

import com.chattlyx.core.analytics.AnalyticsLogger
import com.chattlyx.core.analytics.NoOpAnalyticsLogger
import com.chattlyx.core.common.time.Clock
import com.chattlyx.core.common.time.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** App-wide singletons that have no Android-framework dependency of their own. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock

    /** Phase 0: analytics are off by default (opt-in arrives with Phase 6). */
    @Provides
    @Singleton
    fun provideAnalyticsLogger(): AnalyticsLogger = NoOpAnalyticsLogger
}
