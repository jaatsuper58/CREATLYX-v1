package com.chattlyx.core.database

import android.content.Context
import androidx.room.Room
import com.chattlyx.core.crypto.KeystoreKeyWrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideKeystoreKeyWrapper(
        @ApplicationContext context: Context,
    ): KeystoreKeyWrapper = com.chattlyx.core.crypto.KeystoreKeyWrapperImpl(context)

    /** SQLCipher factory over the Keystore-wrapped passphrase (Section 7.1). */
    @Provides
    @Singleton
    fun provideSupportFactory(keyProvider: DatabaseKeyProvider): SupportOpenHelperFactory =
        SupportOpenHelperFactory(keyProvider.passphrase())

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        factory: SupportOpenHelperFactory,
    ): ChattlyxDatabase = Room.databaseBuilder(context, ChattlyxDatabase::class.java, ChattlyxDatabase.NAME)
        .openHelperFactory(factory)
        .addCallback(ChattlyxDatabase.FTS_SYNC_CALLBACK)
        .build()

    @Provides
    fun provideConversationDao(db: ChattlyxDatabase) = db.conversations()

    @Provides
    fun provideMessageDao(db: ChattlyxDatabase) = db.messages()

    @Provides
    fun provideContactDao(db: ChattlyxDatabase) = db.contacts()
}
