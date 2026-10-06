package com.chattlyx.core.network.rest

import com.chattlyx.core.network.rest.auth.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Production API base; emulator dev builds may point at the local stack. */
object ApiEndpoints {
    const val BASE_URL = "https://api.chattlyx.com/"
}

val ChattlyxJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenProvider: AuthTokenProvider): AuthInterceptor =
        AuthInterceptor(tokenProvider)

    @Provides
    @Singleton
    fun provideTokenRefreshAuthenticator(tokenProvider: AuthTokenProvider): TokenRefreshAuthenticator =
        TokenRefreshAuthenticator(
            tokenProvider = tokenProvider,
            refreshApi = BareHttpRefreshEndpoint(OkHttpClient(), ApiEndpoints.BASE_URL),
        )

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        authenticator: TokenRefreshAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(IdempotencyKeyInterceptor())
        .authenticator(authenticator)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(ApiEndpoints.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(ChattlyxJson.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    @Provides
    @Singleton
    fun provideServiceApi(retrofit: Retrofit): ChattlyxServiceApi =
        retrofit.create(ChattlyxServiceApi::class.java)
}
