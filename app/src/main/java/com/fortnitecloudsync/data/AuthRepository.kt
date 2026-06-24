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
    var displayName: String? = null
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

    fun hasStoredCredentials(): Boolean =
        prefs.getString("refresh_token", null) != null ||
        (prefs.getString("device_account_id", null) != null &&
         prefs.getString("device_device_id", null) != null &&
         prefs.getString("device_secret", null) != null)

    fun clearStoredCredentials() {
        prefs.edit()
            .remove("refresh_token")
            .remove("device_account_id")
            .remove("device_device_id")
            .remove("device_secret")
            .remove("display_name")
            .apply()
        accessToken = null
        accountId = null
        displayName = null
    }

    private fun saveRefreshToken(token: String) {
        prefs.edit().putString("refresh_token", token).apply()
    }

    private fun saveDeviceCredentials(devAccountId: String, deviceId: String, secret: String) {
        prefs.edit()
            .putString("device_account_id", devAccountId)
            .putString("device_device_id", deviceId)
            .putString("device_secret", secret)
            .apply()
    }

    private fun saveDisplayName(name: String) {
        prefs.edit().putString("display_name", name).apply()
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
                    body.refreshToken?.let { saveRefreshToken(it) }
                    fetchAndSaveDisplayName()
                    createDeviceCredentials()
                    val name = displayName ?: id
                    Result.success("Authentication successful! Logged in as $name")
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

    // Try refresh token first (fast), fall back to device auth (reliable long-term)
    suspend fun autoLogin(): Result<String> = withContext(Dispatchers.IO) {
        val storedRefreshToken = prefs.getString("refresh_token", null)
        if (storedRefreshToken != null) {
            val refreshResult = tryRefreshToken(storedRefreshToken)
            if (refreshResult.isSuccess) return@withContext refreshResult
        }

        val savedAccountId = prefs.getString("device_account_id", null)
        val deviceId = prefs.getString("device_device_id", null)
        val secret = prefs.getString("device_secret", null)
        if (savedAccountId != null && deviceId != null && secret != null) {
            return@withContext tryDeviceAuth(savedAccountId, deviceId, secret)
        }

        Result.failure(Exception("No stored credentials"))
    }

    private suspend fun tryRefreshToken(token: String): Result<String> {
        return try {
            val response = NetworkModule.epicGamesApi.refreshToken(
                authorization = getBasicAuthHeader(),
                refreshToken = token
            )
            if (response.isSuccessful) {
                val body = response.body()
                val newToken = body?.accessToken
                val id = body?.accountId
                if (newToken != null && id != null) {
                    accessToken = newToken
                    accountId = id
                    // Epic doesn't always rotate refresh tokens; keep existing one if no new one
                    body.refreshToken?.let { saveRefreshToken(it) }
                    displayName = prefs.getString("display_name", null)
                    if (displayName == null) fetchAndSaveDisplayName()
                    val name = displayName ?: id
                    Result.success("Auto-login successful! Logged in as $name")
                } else {
                    Result.failure(Exception("Missing token in refresh response"))
                }
            } else {
                prefs.edit().remove("refresh_token").apply()
                Result.failure(Exception("Refresh failed (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    private suspend fun tryDeviceAuth(savedAccountId: String, deviceId: String, secret: String): Result<String> {
        return try {
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
                    body.refreshToken?.let { saveRefreshToken(it) }
                    displayName = prefs.getString("display_name", null)
                    if (displayName == null) fetchAndSaveDisplayName()
                    val name = displayName ?: id
                    Result.success("Auto-login successful! Logged in as $name")
                } else {
                    Result.failure(Exception("Missing token in device auth response"))
                }
            } else {
                clearStoredCredentials()
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Device auth failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    private suspend fun fetchAndSaveDisplayName() {
        try {
            val response = NetworkModule.epicGamesApi.getAccountInfo(
                authorization = getBearerHeader(),
                accountId = accountId!!
            )
            if (response.isSuccessful) {
                val name = response.body()?.displayName
                if (name != null) {
                    displayName = name
                    saveDisplayName(name)
                }
            }
        } catch (_: Exception) {}
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
        } catch (_: Exception) {}
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
