package com.chattlyx.data.social

import com.chattlyx.domain.messaging.SearchRepository
import com.chattlyx.domain.social.BlockRepository
import com.chattlyx.domain.social.PresenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SocialBindings {

    @Binds
    fun bindPresenceRepository(impl: PresenceRepositoryImpl): PresenceRepository

    @Binds
    fun bindBlockRepository(impl: BlockRepositoryImpl): BlockRepository
}
