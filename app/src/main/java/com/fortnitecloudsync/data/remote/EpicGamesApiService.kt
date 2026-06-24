package com.fortnitecloudsync.data.remote

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface EpicGamesApiService {

    @POST("account/api/oauth/token")
    @FormUrlEncoded
    suspend fun exchangeCode(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("token_type") tokenType: String = "eg1"
    ): Response<TokenResponse>

    @POST("account/api/oauth/token")
    @FormUrlEncoded
    suspend fun deviceAuthLogin(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "device_auth",
        @Field("account_id") accountId: String,
        @Field("device_id") deviceId: String,
        @Field("secret") secret: String,
        @Field("token_type") tokenType: String = "eg1"
    ): Response<TokenResponse>

    @POST("account/api/oauth/token")
    @FormUrlEncoded
    suspend fun refreshToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("token_type") tokenType: String = "eg1"
    ): Response<TokenResponse>

    @POST("account/api/public/account/{accountId}/deviceAuth")
    suspend fun createDeviceAuth(
        @Header("Authorization") authorization: String,
        @Path("accountId") accountId: String
    ): Response<DeviceAuthResponse>

    @GET("account/api/public/account/{accountId}")
    suspend fun getAccountInfo(
        @Header("Authorization") authorization: String,
        @Path("accountId") accountId: String
    ): Response<AccountInfoResponse>

    @GET("account/api/oauth/verify")
    suspend fun verifyToken(
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
