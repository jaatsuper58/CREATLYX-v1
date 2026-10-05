package com.chattlyx.data.messaging

import com.chattlyx.core.push.PushWakeHandler
import com.chattlyx.domain.messaging.ContactRepository
import com.chattlyx.domain.messaging.RealtimeEvents
import com.chattlyx.domain.messaging.ConversationRepository
import com.chattlyx.domain.messaging.MessageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface MessagingBindings {

    @Binds
    fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    fun bindConversationRepository(impl: ConversationRepositoryImpl): ConversationRepository

    @Binds
    fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    fun bindPeerKeyResolver(impl: PeerKeyResolverImpl): PeerKeyResolver

    @Binds
    fun bindPushWakeHandler(impl: PushWakeHandlerImpl): PushWakeHandler

    @Binds
    fun bindRealtimeEvents(impl: RealtimeCoordinator): RealtimeEvents

    @Binds
    fun bindSearchRepository(impl: SearchRepositoryImpl): com.chattlyx.domain.messaging.SearchRepository
}
