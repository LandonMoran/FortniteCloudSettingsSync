package com.fortnitecloudsync.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fortnitecloudsync.data.AuthRepository
import com.fortnitecloudsync.data.CloudStorageRepository
import com.fortnitecloudsync.data.model.CloudFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val cloudFiles: List<CloudFile> = emptyList(),
    val statusMessages: List<String> = emptyList(),
    val filterRestricted: Boolean = true,
    val selectedFile: CloudFile? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository()
    private val cloudRepository = CloudStorageRepository(authRepository)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val timestampFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        log("Ready. Please login with your Epic Games account.")
        log("Note: Uploading files will automatically replace any existing files with the same name.")
        log("Note: UUID-named files and some platform files (e.g. Switch) are restricted and filtered out by default.")
    }

    fun getAuthorizationUrl(): String = authRepository.getAuthorizationUrl()

    fun authenticate(input: String) {
        log("Received input: ${input.take(100)}${if (input.length > 100) "..." else ""}")
        
        val code = extractCode(input)
        if (code == null) {
            log("❌ No authorization code found in input.")
            log("Please paste the FULL URL, JSON response, or the code itself.")
            log("Input started with: ${input.take(50)}")
            return
        }
        log("✅ Authorization code extracted: ${code.take(6)}...${code.takeLast(4)}")
        log("Authenticating with Epic Games...")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            authRepository.exchangeCode(code)
                .onSuccess { message ->
                    log(message)
                    _state.value = _state.value.copy(
                        isAuthenticated = true,
                        isLoading = false
                    )
                    refreshFiles()
                }
                .onFailure { e ->
                    log("❌ Authentication failed: ${e.message}")
                    _state.value = _state.value.copy(isLoading = false)
                }
        }
    }

    fun logout() {
        _state.value = AppState()
        log("Logged out. Please login again.")
    }

    fun refreshFiles() {
        if (!authRepository.isAuthenticated) {
            log("Please authenticate first.")
            return
        }
        log("Fetching cloud storage file list...")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            cloudRepository.listFiles(_state.value.filterRestricted)
                .onSuccess { (files, filteredCount) ->
                    val msg = if (filteredCount > 0)
                        "Found ${files.size} files in cloud storage (filtered $filteredCount restricted files)"
                    else
                        "Found ${files.size} files in cloud storage"
                    log(msg)
                    _state.value = _state.value.copy(cloudFiles = files, isLoading = false)
                }
                .onFailure { e ->
                    log("Failed to list files: ${e.message}")
                    _state.value = _state.value.copy(isLoading = false)
                }
        }
    }

    fun toggleFilter(enabled: Boolean) {
        _state.value = _state.value.copy(filterRestricted = enabled)
        log("${if (enabled) "Hiding" else "Showing"} restricted files.")
        refreshFiles()
    }

    fun selectFile(file: CloudFile?) {
        _state.value = _state.value.copy(selectedFile = file)
    }

    fun downloadFile(context: Context, file: CloudFile) {
        log("Downloading ${file.uniqueFilename}...")
        viewModelScope.launch {
            cloudRepository.downloadFile(file.uniqueFilename)
                .onSuccess { bytes ->
                    try {
                        val dir = context.getExternalFilesDir(null) ?: context.filesDir
                        val out = File(dir, file.uniqueFilename)
                        out.writeBytes(bytes)
                        log("Downloaded ${file.uniqueFilename} (${cloudRepository.formatSize(bytes.size.toLong())})")
                        log("Saved to: ${out.absolutePath}")
                    } catch (e: Exception) {
                        log("Failed to save file: ${e.message}")
                    }
                }
                .onFailure { e -> log("Download failed: ${e.message}") }
        }
    }

    fun downloadAllFiles(context: Context) {
        val files = _state.value.cloudFiles
        if (files.isEmpty()) {
            log("No files available to download")
            return
        }
        log("Starting download of all ${files.size} files...")
        viewModelScope.launch {
            var successful = 0
            var failed = 0
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            files.forEach { file ->
                log("Downloading (${successful + failed + 1}/${files.size}): ${file.uniqueFilename}")
                cloudRepository.downloadFile(file.uniqueFilename)
                    .onSuccess { bytes ->
                        try {
                            File(dir, file.uniqueFilename).writeBytes(bytes)
                            successful++
                        } catch (e: Exception) {
                            log("Failed to save ${file.uniqueFilename}: ${e.message}")
                            failed++
                        }
                    }
                    .onFailure { e ->
                        log("Failed to download ${file.uniqueFilename}: ${e.message}")
                        failed++
                    }
            }
            log("Download complete: $successful/${files.size} succeeded, $failed failed")
            if (successful > 0) log("Files saved to: ${dir.absolutePath}")
        }
    }

    fun uploadFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        log("Starting upload of ${uris.size} file(s)...")
        viewModelScope.launch {
            var successful = 0
            var failed = 0
            uris.forEachIndexed { index, uri ->
                val filename = getFilenameFromUri(context, uri)
                if (!cloudRepository.isFileAllowed(filename)) {
                    log("Skipping restricted file: $filename")
                    failed++
                    return@forEachIndexed
                }
                log("Uploading (${index + 1}/${uris.size}): $filename")
                val bytes = try {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                } catch (e: Exception) {
                    null
                }
                if (bytes == null) {
                    log("Failed to read: $filename")
                    failed++
                    return@forEachIndexed
                }
                cloudRepository.uploadFile(filename, bytes)
                    .onSuccess { message ->
                        log(message)
                        successful++
                    }
                    .onFailure { e ->
                        log("Upload failed for $filename: ${e.message}")
                        failed++
                    }
            }
            log("Upload complete: $successful/${uris.size} succeeded, $failed failed")
            refreshFiles()
        }
    }

    fun deleteFile(file: CloudFile) {
        log("Deleting ${file.uniqueFilename}...")
        viewModelScope.launch {
            cloudRepository.deleteFile(file.uniqueFilename)
                .onSuccess { message ->
                    log(message)
                    _state.value = _state.value.copy(selectedFile = null)
                    refreshFiles()
                }
                .onFailure { e -> log("Delete failed: ${e.message}") }
        }
    }

    fun formatFileSize(bytes: Long): String = cloudRepository.formatSize(bytes)

    fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "Unknown"
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        val output = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (fmt in formats) {
            try {
                val parsed = SimpleDateFormat(fmt, Locale.US).parse(dateStr) ?: continue
                return output.format(parsed)
            } catch (_: Exception) {}
        }
        return dateStr
    }

    private fun log(message: String) {
        val ts = timestampFormat.format(Date())
        val entry = "[$ts] $message"
        val current = _state.value.statusMessages.toMutableList()
        current.add(entry)
        if (current.size > 200) current.removeAt(0)
        _state.value = _state.value.copy(statusMessages = current)
    }

    private fun extractCode(input: String): String? {
        val trimmed = input.trim()
        log("extractCode: Processing input (length: ${trimmed.length})")
        
        if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
            log("extractCode: Detected JSON format")
            try {
                val json = JSONObject(trimmed)
                val authCode = json.optString("authorizationCode")
                log("extractCode: JSON authorizationCode field: ${if (authCode.isNotEmpty()) authCode.take(6) + "..." else "empty/not found"}")
                if (authCode.isNotEmpty()) return authCode
                
                val redirectUrl = json.optString("redirectUrl")
                if (redirectUrl.contains("code=")) {
                    log("extractCode: Found redirectUrl with code, extracting...")
                    return extractCodeFromUrl(redirectUrl)
                } else {
                    log("extractCode: redirectUrl doesn't contain code or redirectUrl is: $redirectUrl")
                }
            } catch (_: JSONException) {
                log("extractCode: JSON parsing failed")
            }
        } else if (trimmed.startsWith("http")) {
            log("extractCode: Detected URL format")
            return extractCodeFromUrl(trimmed)
        } else if (Regex("^[a-zA-Z0-9]{20,40}$").matches(trimmed)) {
            log("extractCode: Detected raw code format")
            return trimmed
        } else {
            log("extractCode: Unknown format, starts with: ${trimmed.take(30)}")
        }
        return null
    }

    private fun extractCodeFromUrl(url: String): String? {
        log("extractCodeFromUrl: Parsing URL: ${url.take(80)}...")
        return try {
            val uri = Uri.parse(url)
            val code = uri.getQueryParameter("code")
            if (code != null) {
                log("extractCodeFromUrl: Found code: ${code.take(6)}...")
                code
            } else {
                log("extractCodeFromUrl: No 'code' parameter found in URL")
                null
            }
        } catch (_: Exception) {
            log("extractCodeFromUrl: URL parsing failed")
            null
        }
    }

    private fun getFilenameFromUri(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment ?: "unknown_file"
    }
}
