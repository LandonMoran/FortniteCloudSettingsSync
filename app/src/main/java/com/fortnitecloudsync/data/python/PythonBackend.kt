package com.fortnitecloudsync.data.python

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.fortnitecloudsync.data.model.CloudFile
import org.json.JSONArray
import org.json.JSONObject

data class PythonAuthResult(
    val success: Boolean,
    val message: String,
    val accessToken: String?,
    val accountId: String?,
    val refreshToken: String?
)

data class PythonListFilesResult(
    val success: Boolean,
    val message: String,
    val files: List<CloudFile>,
    val filteredCount: Int
)

object PythonBackend {

    private const val MODULE_NAME = "fortnite_cloud_sync_backend"

    private val module: PyObject by lazy {
        Python.getInstance().getModule(MODULE_NAME)
    }

    private fun call(functionName: String, vararg args: Any?): PyObject =
        module.callAttr(functionName, *args)

    private fun callString(functionName: String, vararg args: Any?): String =
        call(functionName, *args).toString()

    private fun callBoolean(functionName: String, vararg args: Any?): Boolean =
        call(functionName, *args).toBoolean()

    fun clearSession() {
        call("clear_session")
    }

    fun getAuthorizationUrl(): String = callString("get_authorization_url")

    // Returns the parsed authorization code, or null when the input simply did not
    // contain one. Errors from the Python bridge itself (e.g. the backend failing to
    // initialise) are intentionally NOT swallowed here, so callers can distinguish
    // "no code in the input" from "the backend is broken" instead of always blaming
    // the user's input.
    fun extractCodeFromInput(input: String): String? =
        callString("extract_code_from_input", input).trim().takeIf { it.isNotBlank() }

    fun exchangeCodeLogin(code: String): PythonAuthResult {
        val json = JSONObject(callString("exchange_code_login_json", code))
        return PythonAuthResult(
            success = json.optBoolean("success"),
            message = json.optString("message"),
            accessToken = json.optString("access_token").takeIf { it.isNotBlank() },
            accountId = json.optString("account_id").takeIf { it.isNotBlank() },
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
        )
    }

    fun verifyToken(): Boolean = callBoolean("verify_token")

    fun listFiles(filterRestricted: Boolean = true): PythonListFilesResult {
        val json = JSONObject(callString("list_files_json", filterRestricted))
        val filesArray = json.optJSONArray("files")
        val files = if (filesArray != null) {
            parseCloudFiles(filesArray)
        } else {
            emptyList()
        }

        return PythonListFilesResult(
            success = json.optBoolean("success"),
            message = json.optString("message"),
            files = files,
            filteredCount = json.optInt("filtered_count", 0)
        )
    }

    fun downloadFile(uniqueFilename: String): ByteArray =
        call("download_file_bytes", uniqueFilename).toJava(ByteArray::class.java)

    fun uploadFile(uniqueFilename: String, fileData: ByteArray): String =
        callString("upload_file_bytes", uniqueFilename, fileData)

    fun deleteFile(uniqueFilename: String): String =
        callString("delete_file", uniqueFilename)

    private fun parseCloudFiles(filesArray: JSONArray): List<CloudFile> {
        val files = ArrayList<CloudFile>(filesArray.length())
        for (i in 0 until filesArray.length()) {
            val fileObject = filesArray.optJSONObject(i) ?: continue
            parseCloudFile(fileObject)?.let(files::add)
        }
        return files
    }

    private fun parseCloudFile(fileObject: JSONObject): CloudFile? {
        val filename = fileObject.optString("uniqueFilename").takeIf { it.isNotBlank() } ?: return null
        val length = fileObject.optLong("length", 0L)
        val modified = fileObject.optString("lastModified").takeIf { it.isNotBlank() }
            ?: fileObject.optString("uploaded").takeIf { it.isNotBlank() }
            ?: fileObject.optString("updated").takeIf { it.isNotBlank() }
            ?: fileObject.optString("dateModified").takeIf { it.isNotBlank() }
        return CloudFile(
            uniqueFilename = filename,
            length = length,
            lastModified = modified
        )
    }
}
