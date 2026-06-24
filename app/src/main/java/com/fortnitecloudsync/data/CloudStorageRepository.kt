package com.fortnitecloudsync.data

import com.fortnitecloudsync.data.model.CloudFile
import com.fortnitecloudsync.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

class CloudStorageRepository(private val auth: AuthRepository) {

    private val restrictedFiles = setOf("ClientSettingsSwitch.Sav")
    private val uuidPattern = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}_r\\d+_a\\d+\\.sav$",
        Pattern.CASE_INSENSITIVE
    )

    fun isFileAllowed(filename: String): Boolean {
        if (filename in restrictedFiles) return false
        if (uuidPattern.matcher(filename).matches()) return false
        return true
    }

    suspend fun listFiles(filterRestricted: Boolean = true): Result<Pair<List<CloudFile>, Int>> =
        withContext(Dispatchers.IO) {
            if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
            try {
                val response = NetworkModule.fortniteApi.listFiles(
                    authorization = auth.getBearerHeader(),
                    accountId = auth.accountId!!
                )
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: return@withContext Result.success(Pair(emptyList(), 0))
                    val files = parseFilesResponse(body)
                    val filtered = if (filterRestricted) files.filter { isFileAllowed(it.uniqueFilename) } else files
                    val filteredCount = files.size - filtered.size
                    Result.success(Pair(filtered, filteredCount))
                } else {
                    Result.failure(Exception("Failed to list files (${response.code()})"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Network error: ${e.message}"))
            }
        }

    private fun parseFilesResponse(body: String): List<CloudFile> {
        return try {
            val trimmed = body.trim()
            when {
                trimmed.startsWith('[') -> {
                    val arr = JSONArray(body)
                    (0 until arr.length()).mapNotNull { parseCloudFile(arr.getJSONObject(it)) }
                }
                trimmed.startsWith('{') -> {
                    val obj = JSONObject(body)
                    when {
                        obj.has("files") -> {
                            val arr = obj.getJSONArray("files")
                            (0 until arr.length()).mapNotNull { parseCloudFile(arr.getJSONObject(it)) }
                        }
                        obj.has("data") -> {
                            val data = obj.get("data")
                            when (data) {
                                is JSONArray -> (0 until data.length()).mapNotNull { parseCloudFile(data.getJSONObject(it)) }
                                is JSONObject -> listOfNotNull(parseCloudFile(data))
                                else -> emptyList()
                            }
                        }
                        obj.has("items") -> {
                            val arr = obj.getJSONArray("items")
                            (0 until arr.length()).mapNotNull { parseCloudFile(arr.getJSONObject(it)) }
                        }
                        obj.has("uniqueFilename") -> listOfNotNull(parseCloudFile(obj))
                        else -> emptyList()
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseCloudFile(obj: JSONObject): CloudFile? {
        val filename = obj.optString("uniqueFilename").takeIf { it.isNotEmpty() } ?: return null
        val length = obj.optLong("length", 0L)
        val modified = obj.optString("lastModified").takeIf { it.isNotEmpty() }
            ?: obj.optString("uploaded").takeIf { it.isNotEmpty() }
            ?: obj.optString("updated").takeIf { it.isNotEmpty() }
            ?: obj.optString("dateModified").takeIf { it.isNotEmpty() }
        return CloudFile(filename, length, modified)
    }

    suspend fun downloadFile(filename: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
        if (!isFileAllowed(filename)) return@withContext Result.failure(Exception("File is restricted"))
        try {
            val encoded = URLEncoder.encode(filename, "UTF-8")
            val response = NetworkModule.fortniteApi.downloadFile(
                authorization = auth.getBearerHeader(),
                accountId = auth.accountId!!,
                filename = encoded
            )
            if (response.isSuccessful) {
                Result.success(response.body()?.bytes() ?: byteArrayOf())
            } else {
                Result.failure(Exception("Download failed (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    suspend fun uploadFile(filename: String, data: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
            if (!isFileAllowed(filename)) return@withContext Result.failure(Exception("File is restricted"))
            try {
                val fileExists = listFiles(false)
                    .getOrNull()?.first
                    ?.any { it.uniqueFilename == filename } == true
                val encoded = URLEncoder.encode(filename, "UTF-8")
                val body = data.toRequestBody("application/octet-stream".toMediaType())
                val response = NetworkModule.fortniteApi.uploadFile(
                    authorization = auth.getBearerHeader(),
                    contentType = "application/octet-stream",
                    accountId = auth.accountId!!,
                    filename = encoded,
                    body = body
                )
                if (response.isSuccessful) {
                    val action = if (fileExists) "Replaced" else "Uploaded"
                    Result.success("$action $filename (${formatSize(data.size.toLong())})")
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Result.failure(Exception("Upload failed (${response.code()}): $errorBody"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Network error: ${e.message}"))
            }
        }

    suspend fun deleteFile(filename: String): Result<String> = withContext(Dispatchers.IO) {
        if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
        if (!isFileAllowed(filename)) return@withContext Result.failure(Exception("File is restricted"))
        try {
            val encoded = URLEncoder.encode(filename, "UTF-8")
            val response = NetworkModule.fortniteApi.deleteFile(
                authorization = auth.getBearerHeader(),
                accountId = auth.accountId!!,
                filename = encoded
            )
            if (response.isSuccessful) {
                Result.success("Deleted $filename")
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Delete failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error: ${e.message}"))
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}
