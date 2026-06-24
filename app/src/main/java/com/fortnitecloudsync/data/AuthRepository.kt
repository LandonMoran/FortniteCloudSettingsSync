package com.fortnitecloudsync.data

import android.util.Base64
import com.fortnitecloudsync.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// Authentication repository matching Python script exactly
class AuthRepository {

    private val clientId = "ec684b8c687f479fadea3cb2ad83f5c6"
    private val clientSecret = "e1f31c211f28413186262d37a13fc84d"

    var accessToken: String? = null
        private set
    var accountId: String? = null
        private set

    val isAuthenticated: Boolean
        get() = accessToken != null && accountId != null

    fun clearSession() {
        accessToken = null
        accountId = null
    }

    fun getAuthorizationUrl(): String =
        "https://www.epicgames.com/id/logout?redirectUrl=https%3A//www.epicgames.com/id/login%3FredirectUrl%3Dhttps%253A//www.epicgames.com/id/api/redirect%253FclientId%253Dec684b8c687f479fadea3cb2ad83f5c6%2526responseType%253Dcode"

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
                    Result.failure(Exception("Authentication failed: Missing token or account ID"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = try {
                    errorBody?.let { JSONObject(it).optString("errorMessage") }
                        ?.takeIf { it.isNotBlank() }
                        ?: errorBody
                        ?: "Unknown error"
                } catch (_: Exception) {
                    errorBody ?: "Unknown error"
                }
                Result.failure(Exception("Authentication failed: ${response.code()}\nError: $errorMessage"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error during authentication: ${e.message}"))
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
