package com.chattlyx.core.database

import android.content.Context
import com.chattlyx.core.crypto.KeystoreKeyWrapper
import com.chattlyx.core.crypto.KeystoreKeyWrapperImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideKeystoreKeyWrapper(
        @ApplicationContext context: Context,
    ): KeystoreKeyWrapper = KeystoreKeyWrapperImpl(context)
}
