package com.chattlyx.data.calls

import com.chattlyx.domain.calls.CallHistoryRepository
import com.chattlyx.domain.calls.CallSession
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface CallsBindings {

    @Binds
    fun bindCallSession(impl: CallManager): CallSession

    @Binds
    fun bindCallHistory(impl: CallManager): CallHistoryRepository

    companion object {

        /** One engine for the app lifetime; peer connections are per call. */
        @Provides
        @Singleton
        fun provideCallEngine(
            @dagger.hilt.android.qualifiers.ApplicationContext
            context: android.content.Context,
        ): com.chattlyx.core.rtc.WebRtcCallEngine =
            com.chattlyx.core.rtc.WebRtcCallEngine(context)
    }
}
