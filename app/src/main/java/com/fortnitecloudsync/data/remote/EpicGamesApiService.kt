package com.fortnitecloudsync.data.remote

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface EpicGamesApiService {

    @POST("account/api/oauth/token")
    @FormUrlEncoded
    suspend fun exchangeCode(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("token_type") tokenType: String = "eg1"
    ): Response<TokenResponse>

    @GET("account/api/oauth/verify")
    suspend fun verifyToken(
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
