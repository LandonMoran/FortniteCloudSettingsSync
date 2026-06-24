package com.fortnitecloudsync.data

import com.fortnitecloudsync.data.python.PythonBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Authentication repository backed by the embedded Python module.
class AuthRepository {

    var accessToken: String? = null
        private set
    var accountId: String? = null
        private set
    var refreshToken: String? = null
        private set

    val isAuthenticated: Boolean
        get() = accessToken != null && accountId != null

    fun clearSession() {
        PythonBackend.clearSession()
        accessToken = null
        accountId = null
        refreshToken = null
    }

    fun getAuthorizationUrl(): String = PythonBackend.getAuthorizationUrl()

    suspend fun extractCode(input: String): String? = withContext(Dispatchers.Default) {
        PythonBackend.extractCodeFromInput(input)
    }

    suspend fun exchangeCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = PythonBackend.exchangeCodeLogin(code)

            if (result.success && result.accessToken != null && result.accountId != null) {
                accessToken = result.accessToken
                accountId = result.accountId
                refreshToken = result.refreshToken
                Result.success(result.message)
            } else {
                Result.failure(Exception(result.message.ifBlank { "Authentication failed" }))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Authentication failed: ${e.message ?: "Unknown error"}"))
        }
    }

    suspend fun verifyToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            accessToken != null && PythonBackend.verifyToken()
        } catch (_: Exception) {
            false
        }
    }
}
