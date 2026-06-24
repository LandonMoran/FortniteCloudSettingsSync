package com.fortnitecloudsync.data

import android.content.Context
import android.util.Base64
import com.fortnitecloudsync.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(context: Context) {

    private val clientId = "ec684b8c687f479fadea3cb2ad83f5c6"
    private val clientSecret = "e1f31c211f28413186262d37a13fc84d"

    private val prefs = context.getSharedPreferences("fortnitecloudsync.prefs", Context.MODE_PRIVATE)

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

    fun hasStoredDeviceCredentials(): Boolean =
        prefs.getString("device_account_id", null) != null &&
        prefs.getString("device_device_id", null) != null &&
        prefs.getString("device_secret", null) != null

    fun clearStoredCredentials() {
        prefs.edit()
            .remove("device_account_id")
            .remove("device_device_id")
            .remove("device_secret")
            .apply()
        accessToken = null
        accountId = null
    }

    private fun saveDeviceCredentials(devAccountId: String, deviceId: String, secret: String) {
        prefs.edit()
            .putString("device_account_id", devAccountId)
            .putString("device_device_id", deviceId)
            .putString("device_secret", secret)
            .apply()
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
                    createDeviceCredentials()
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

    suspend fun loginWithDeviceAuth(): Result<String> = withContext(Dispatchers.IO) {
        val savedAccountId = prefs.getString("device_account_id", null)
        val deviceId = prefs.getString("device_device_id", null)
        val secret = prefs.getString("device_secret", null)

        if (savedAccountId == null || deviceId == null || secret == null) {
            return@withContext Result.failure(Exception("No stored device credentials"))
        }

        try {
            val response = NetworkModule.epicGamesApi.deviceAuthLogin(
                authorization = getBasicAuthHeader(),
                accountId = savedAccountId,
                deviceId = deviceId,
                secret = secret
            )

            if (response.isSuccessful) {
                val body = response.body()
                val token = body?.accessToken
                val id = body?.accountId

                if (token != null && id != null) {
                    accessToken = token
                    accountId = id
                    Result.success("Auto-login successful! Account ID: $id")
                } else {
                    Result.failure(Exception("Missing token or account ID in response"))
                }
            } else {
                clearStoredCredentials()
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Auto-login failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    private suspend fun createDeviceCredentials() {
        try {
            val response = NetworkModule.epicGamesApi.createDeviceAuth(
                authorization = getBearerHeader(),
                accountId = accountId!!
            )
            if (response.isSuccessful) {
                val body = response.body()
                val devAccountId = body?.accountId
                val deviceId = body?.deviceId
                val devSecret = body?.secret
                if (devAccountId != null && deviceId != null && devSecret != null) {
                    saveDeviceCredentials(devAccountId, deviceId, devSecret)
                }
            }
        } catch (_: Exception) {
            // Device credential creation is best-effort; don't fail the main auth flow
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
