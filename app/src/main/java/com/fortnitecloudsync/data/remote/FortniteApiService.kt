package com.fortnitecloudsync.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path

interface FortniteApiService {

    @GET("fortnite/api/cloudstorage/user/{accountId}")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Path("accountId") accountId: String
    ): Response<ResponseBody>

    @GET("fortnite/api/cloudstorage/user/{accountId}/{filename}")
    suspend fun downloadFile(
        @Header("Authorization") authorization: String,
        @Path("accountId") accountId: String,
        @Path(value = "filename", encoded = true) filename: String
    ): Response<ResponseBody>

    @PUT("fortnite/api/cloudstorage/user/{accountId}/{filename}")
    suspend fun uploadFile(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Path("accountId") accountId: String,
        @Path(value = "filename", encoded = true) filename: String,
        @Body body: RequestBody
    ): Response<Unit>

    @DELETE("fortnite/api/cloudstorage/user/{accountId}/{filename}")
    suspend fun deleteFile(
        @Header("Authorization") authorization: String,
        @Path("accountId") accountId: String,
        @Path(value = "filename", encoded = true) filename: String
    ): Response<Unit>
}
