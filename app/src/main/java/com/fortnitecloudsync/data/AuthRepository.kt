package com.fortnitecloudsync.data

import android.util.Base64
import com.fortnitecloudsync.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository {

    private val clientId = "ec684b8c687f479fadea3cb2ad83f5c6"
    private val clientSecret = "e1f31c211f28413186262d37a13fc84d"

    var accessToken: String? = null
        private set
    var accountId: String? = null
        private set

    val isAuthenticated: Boolean
        get() = accessToken != null && accountId != null

    fun getAuthorizationUrl(): String =
        "https://www.epicgames.com/id/logout?" +
        "redirectUrl=https%3A//www.epicgames.com/id/login%3F" +
        "redirectUrl%3Dhttps%253A//www.epicgames.com/id/api/redirect%253F" +
        "clientId%253Dec684b8c687f479fadea3cb2ad83f5c6%2526responseType%253Dcode"

    fun getBearerHeader(): String = "bearer $accessToken"

    private fun getBasicAuthHeader(): String {
        val authString = "$clientId:$clientSecret"
        return "basic ${Base64.encodeToString(authString.toByteArray(), Base64.NO_WRAP)}"
    }

    suspend fun exchangeCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = NetworkModule.epicGamesApi.exchangeCode(
                authorization = getBasicAuthHeader(),
                code = code
            )

            if (response.isSuccessful) {
                val body = response.body()
                val token = body?.accessToken
                val id = body?.accountId

                if (token != null && id != null) {
                    accessToken = token
                    accountId = id
                    Result.success("Authentication successful! Account ID: $id")
                } else {
                    Result.failure(Exception("Missing token or account ID in response"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Authentication failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun verifyToken(): Boolean = withContext(Dispatchers.IO) {
        if (accessToken == null) return@withContext false
        try {
            NetworkModule.epicGamesApi.verifyToken(getBearerHeader()).isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
