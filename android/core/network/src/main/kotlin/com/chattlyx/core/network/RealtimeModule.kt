package com.chattlyx.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RealtimeBindings {

    @Binds
    fun bindRealtimeClient(impl: OkHttpRealtimeClient): RealtimeClient
}
