package com.chattlyx.data.groups

import com.chattlyx.data.auth.SecureTokenStore
import com.chattlyx.domain.groups.GroupRepository
import com.chattlyx.domain.groups.SelfAccountIdProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface GroupsBindings {

    @Binds
    fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    companion object {
        /** Bridges the auth token store into the domain layer. */
        @Provides
        fun provideSelfAccountIdProvider(tokenStore: SecureTokenStore): SelfAccountIdProvider =
            SelfAccountIdProvider { tokenStore.accountId() }
    }
}
