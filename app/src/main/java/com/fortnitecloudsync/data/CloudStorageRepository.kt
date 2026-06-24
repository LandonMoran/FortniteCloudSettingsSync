package com.fortnitecloudsync.data

import com.fortnitecloudsync.data.model.CloudFile
import com.fortnitecloudsync.data.remote.NetworkModule
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun listFiles(filterRestricted: Boolean = true): Result<List<CloudFile>> =
        withContext(Dispatchers.IO) {
            if (!auth.isAuthenticated) return@withContext Result.failure(Exception("Authentication required"))
            try {
                val response = NetworkModule.fortniteApi.listFiles(
                    authorization = auth.getBearerHeader(),
                    accountId = auth.accountId!!
                )
                if (response.isSuccessful) {
                    val body = response.body()?.string() ?: return@withContext Result.success(emptyList())
                    val files = parseFilesResponse(body)
                    val filtered = if (filterRestricted) files.filter { isFileAllowed(it.uniqueFilename) } else files
                    Result.success(filtered)
                } else {
                    Result.failure(Exception("Failed to list files (${response.code()})"))
                }
            } catch (e: Exception) {
                Result.failure(Exception("Network error: ${e.message}"))
            }
        }

    private fun parseFilesResponse(body: String): List<CloudFile> {
        return try {
            val element = JsonParser.parseString(body)
            when {
                element.isJsonArray -> element.asJsonArray.mapNotNull { parseCloudFile(it.asJsonObject) }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    when {
                        obj.has("files") -> obj.getAsJsonArray("files").mapNotNull { parseCloudFile(it.asJsonObject) }
                        obj.has("data") -> {
                            val data = obj.get("data")
                            if (data.isJsonArray) data.asJsonArray.mapNotNull { parseCloudFile(it.asJsonObject) }
                            else if (data.isJsonObject) listOfNotNull(parseCloudFile(data.asJsonObject))
                            else emptyList()
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

    private fun parseCloudFile(obj: JsonObject): CloudFile? {
        val filename = obj.get("uniqueFilename")?.asString ?: return null
        val length = obj.get("length")?.asLong ?: 0L
        val modified = obj.get("lastModified")?.asString
            ?: obj.get("uploaded")?.asString
            ?: obj.get("updated")?.asString
            ?: obj.get("dateModified")?.asString
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
                    Result.success("Uploaded $filename (${formatSize(data.size.toLong())})")
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
