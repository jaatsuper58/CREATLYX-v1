package com.chattlyx.data.calls

import com.chattlyx.domain.calls.CallHistoryRepository
import com.chattlyx.domain.calls.CallSession
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface CallsBindings {

    @Binds
    fun bindCallSession(impl: CallManager): CallSession

    @Binds
    fun bindCallHistory(impl: CallManager): CallHistoryRepository
}
