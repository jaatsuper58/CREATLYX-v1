package com.chattlyx.data.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.chattlyx.core.crypto.KeystoreKeyWrapper
import com.chattlyx.core.crypto.KeystoreKeyWrapperImpl
import com.chattlyx.core.network.rest.AuthTokenProvider
import com.chattlyx.domain.auth.AuthRepository
import com.chattlyx.domain.auth.AvatarImageSource
import com.chattlyx.domain.auth.DeviceRepository
import com.chattlyx.domain.auth.KeysRepository
import com.chattlyx.domain.auth.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
interface AuthBindings {

    @Binds
    fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    fun bindDeviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    fun bindKeysRepository(impl: KeysRepositoryImpl): KeysRepository

    @Binds
    fun bindTokenProvider(impl: SecureTokenStore): AuthTokenProvider

    @Binds
    fun bindDeviceNameProvider(impl: BuildModelDeviceNameProvider): DeviceNameProvider

    @Binds
    fun bindAvatarImageSource(impl: AvatarImageSourceImpl): AvatarImageSource
}

@Module
@InstallIn(SingletonComponent::class)
object AuthDataModule {

    @Provides
    @Singleton
    fun provideKeystoreKeyWrapper(@ApplicationContext context: Context): KeystoreKeyWrapper =
        KeystoreKeyWrapperImpl(context)

    /** Separate DataStore for credentials so settings wipes never touch tokens. */
    @Provides
    @Singleton
    @AuthDataStore
    fun provideAuthDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ) {
            context.preferencesDataStoreFile("chattlyx_auth")
        }
}

/** Qualifier distinguishing the credential DataStore from settings. */
@Retention(AnnotationRetention.RUNTIME)
@javax.inject.Qualifier
annotation class AuthDataStore
