package com.chattlyx.core.network.rest

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for the Phase 1 REST API (AUTH-02..07, AUTH-10, key
 * distribution). Responses are wrapped in [Response] so the call layer can
 * map status codes to the client error taxonomy.
 */
interface ChattlyxServiceApi {

    // --- AUTH-02/03/07 ---

    @POST("v1/auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestDto): Response<OtpRequestResultDto>

    @POST("v1/auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyDto): Response<TokenPairDto>

    @POST("v1/auth/token/refresh")
    suspend fun refreshTokens(@Body body: RefreshTokenDto): Response<TokenPairDto>

    // --- AUTH-04 profile ---

    @GET("v1/profile")
    suspend fun getProfile(): Response<ProfileDto>

    @PUT("v1/profile")
    suspend fun updateProfile(@Body body: ProfileUpdateDto): Response<ProfileDto>

    @POST("v1/profile/avatar")
    suspend fun uploadAvatar(@Body ciphertext: RequestBody): Response<AvatarUploadResultDto>

    @GET("v1/profile/avatar/{blobId}")
    suspend fun downloadAvatar(@Path("blobId") blobId: String): Response<okhttp3.ResponseBody>

    // --- AUTH-07 devices, AUTH-10 deletion ---

    @GET("v1/devices")
    suspend fun getDevices(): Response<DeviceListDto>

    @DELETE("v1/devices/{deviceId}")
    suspend fun revokeDevice(@Path("deviceId") deviceId: Long): Response<Unit>

    @DELETE("v1/account")
    suspend fun deleteAccount(): Response<Unit>

    // --- AUTH-06 keys ---

    @PUT("v1/devices/keys")
    suspend fun uploadKeys(@Body body: UploadKeysDto): Response<KeyCountDto>

    @GET("v1/keys/count")
    suspend fun keyCount(): Response<KeyCountDto>

    @GET("v1/keys/{accountId}/{deviceId}")
    suspend fun keyBundle(
        @Path("accountId") accountId: String,
        @Path("deviceId") deviceId: Long,
    ): Response<KeyBundleDto>

    /** AUTH-06 directory: bundles for all active devices of an account. */
    @GET("v1/keys/{accountId}")
    suspend fun keyBundles(@Path("accountId") accountId: String): Response<KeyBundleListDto>

    // --- Phase 2 ---

    /** MSG-06: unacked envelopes for a conversation past a cursor. */
    @GET("v1/messages/{conversationId}")
    suspend fun history(
        @Path("conversationId", encoded = true) conversationId: String,
        @Query("afterSeq") afterSeq: Long,
        @Query("limit") limit: Int,
    ): Response<HistoryResponseDto>

    /** CON-03: private contact discovery by SHA-256 E.164 hashes. */
    @POST("v1/contacts/discovery")
    suspend fun discoverContacts(@Body body: DiscoveryRequestDto): Response<DiscoveryResponseDto>

    /** NOT-01: register the FCM data-only push token. */
    @PUT("v1/devices/push")
    suspend fun registerPushToken(@Body body: PushTokenDto): Response<Unit>
}
