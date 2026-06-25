package com.fortnitecloudsync.data

import android.util.Log
import com.fortnitecloudsync.data.model.CloudFile
import com.fortnitecloudsync.data.python.PythonBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class CloudStorageRepository(private val auth: AuthRepository) {

    companion object {
        private const val TAG = "CloudStorageRepo"
    }

    private val restrictedFiles = setOf("ClientSettingsSwitch.Sav")
    private val uuidPattern = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_r\\d+_a\\d+\\.sav$",
        Pattern.CASE_INSENSITIVE
    )
    private val blacklistedPatterns: List<Pattern> = emptyList()

    fun isFileAllowed(filename: String): Boolean {
        if (filename in restrictedFiles) return false
        if (uuidPattern.matcher(filename).matches()) return false
        for (pattern in blacklistedPatterns) {
            if (pattern.matcher(filename).find()) return false
        }
        return true
    }

    suspend fun listFiles(filterRestricted: Boolean = true): Result<Pair<List<CloudFile>, Int>> =
        withContext(Dispatchers.IO) {
            if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
            try {
                val result = PythonBackend.listFiles(filterRestricted)
                Log.d(TAG, result.message)
                if (result.success) {
                    Result.success(result.files to result.filteredCount)
                } else {
                    Result.failure(Exception(result.message.ifBlank { "Failed to list files" }))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during listFiles", e)
                Result.failure(Exception("Network error: ${e.message ?: "Unknown error"}"))
            }
        }

    suspend fun downloadFile(filename: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
        if (!isFileAllowed(filename)) return@withContext Result.failure(Exception("File is restricted"))
        try {
            Result.success(PythonBackend.downloadFile(filename))
        } catch (e: Exception) {
            Log.e(TAG, "Download exception", e)
            Result.failure(Exception("Network error: ${e.message ?: "Unknown error"}"))
        }
    }

    suspend fun uploadFile(filename: String, data: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
            if (!isFileAllowed(filename)) return@withContext Result.failure(Exception("File is restricted"))
            try {
                Result.success(PythonBackend.uploadFile(filename, data))
            } catch (e: Exception) {
                Log.e(TAG, "Upload exception", e)
                Result.failure(Exception("Network error: ${e.message ?: "Unknown error"}"))
            }
        }

    suspend fun deleteFile(filename: String): Result<String> = withContext(Dispatchers.IO) {
        if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
        if (!isFileAllowed(filename)) return@withContext Result.failure(Exception("File is restricted"))
        try {
            Result.success(PythonBackend.deleteFile(filename))
        } catch (e: Exception) {
            Log.e(TAG, "Delete exception", e)
            Result.failure(Exception("Network error: ${e.message ?: "Unknown error"}"))
        }
    }

    // Pure-Kotlin formatting (matches the Python format_size output). This is called
    // from Compose during composition for every file row, so it must not block the UI
    // thread with a synchronous Chaquopy/Python interop call.
    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}
