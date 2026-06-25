package com.fortnitecloudsync.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fortnitecloudsync.data.AuthRepository
import com.fortnitecloudsync.data.CloudStorageRepository
import com.fortnitecloudsync.data.DownloadSaver
import com.fortnitecloudsync.data.model.CloudFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.ParsePosition
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
        launchWithLoading {
            val code = try {
                authRepository.extractCode(input)
            } catch (e: Exception) {
                log("❌ Authentication backend error: ${e.message ?: "unknown error"}")
                log("The embedded Python backend failed to run. Try reinstalling the app.")
                return@launchWithLoading
            }
            if (code.isNullOrBlank()) {
                log("❌ No authorization code found in input.")
                log("Please paste the FULL URL, JSON response, or the code itself.")
                return@launchWithLoading
            }

            log("🔑 Authorization code extracted: ${code.take(6)}...${code.takeLast(4)}")
            log("🔄 Authenticating with Epic Games...")

            authRepository.exchangeCode(code)
                .onSuccess { message ->
                    log(message)
                    _state.value = _state.value.copy(isAuthenticated = true)
                    refreshFilesInternal()
                }
                .onFailure { e ->
                    log("❌ Authentication failed: ${e.message}")
                    log("⚠️ Epic codes are single-use and expire in ~5 min.")
                    log("Tap 'Login with Epic Games' to get a FRESH code, then Authenticate right away. Do not reuse a code.")
                }
        }
    }

    fun logout() {
        // Reset the UI first so the user always lands back on the login screen —
        // the transition must never depend on the backend call below.
        _state.value = AppState()
        // Guard the backend session-clear. Every other call into the embedded
        // Python bridge is wrapped in error handling; logout was the lone
        // exception, so any throwable from clearSession() propagated uncaught on
        // the main thread and crashed the entire app. The session is already
        // gone from the UI's perspective, so a cleanup failure is non-fatal.
        runCatching { authRepository.clearSession() }
            .onFailure { e -> log("⚠️ Backend logout cleanup failed: ${e.message}") }
        log("Logged out. Please login again.")
    }

    private fun launchWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                block()
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun refreshFiles() {
        launchWithLoading { refreshFilesInternal() }
    }

    private suspend fun refreshFilesInternal() {
        if (!authRepository.isAuthenticated) {
            log("Please authenticate first.")
            return
        }
        log("🔄 Fetching cloud storage file list...")
        cloudRepository.listFiles(_state.value.filterRestricted)
            .onSuccess { (files, filteredCount) ->
                val msg = if (filteredCount > 0) {
                    "Found ${files.size} files in cloud storage (filtered $filteredCount restricted files)"
                } else {
                    "Found ${files.size} files in cloud storage"
                }
                log(msg)
                _state.value = _state.value.copy(cloudFiles = files)
            }
            .onFailure { e ->
                log("Failed to list files: ${e.message}")
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
        log("⬇️ Downloading ${file.uniqueFilename}...")
        launchWithLoading {
            cloudRepository.downloadFile(file.uniqueFilename)
                .onSuccess { bytes ->
                    saveBytes(context, file.uniqueFilename, bytes)
                        .onSuccess { location ->
                            log("Downloaded ${file.uniqueFilename} (${cloudRepository.formatSize(bytes.size.toLong())})")
                            log("Saved to: $location")
                        }
                        .onFailure { e -> log("Failed to save file: ${e.message}") }
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
        log("⬇️ Starting download of all ${files.size} files...")
        launchWithLoading {
            var successful = 0
            var failed = 0
            files.forEach { file ->
                log("Downloading (${successful + failed + 1}/${files.size}): ${file.uniqueFilename}")
                cloudRepository.downloadFile(file.uniqueFilename)
                    .onSuccess { bytes ->
                        saveBytes(context, file.uniqueFilename, bytes)
                            .onSuccess { successful++ }
                            .onFailure { e ->
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
            if (successful > 0) log("Files saved to: Downloads/FortniteCloudSync")
        }
    }

    // Saves to the public Downloads/FortniteCloudSync folder; if that fails
    // (e.g. older Android without storage permission), falls back to the
    // app-private folder so a download is never lost.
    private fun saveBytes(context: Context, filename: String, bytes: ByteArray): Result<String> =
        DownloadSaver.save(context, filename, bytes).recoverCatching { error ->
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            File(dir, filename).writeBytes(bytes)
            log("⚠️ Saved to app folder (couldn't reach Downloads: ${error.message})")
            File(dir, filename).absolutePath
        }

    fun uploadFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        log("⬆️ Starting upload of ${uris.size} file(s)...")
        launchWithLoading {
            var successful = 0
            var failed = 0
            uris.forEachIndexed { index, uri ->
                val filename = getFilenameFromUri(context, uri)
                if (filename == null) {
                    log("Could not determine filename for URI: $uri")
                    failed++
                    return@forEachIndexed
                }
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
            refreshFilesInternal()
        }
    }

    fun deleteFile(file: CloudFile) {
        log("🗑️ Deleting ${file.uniqueFilename}...")
        launchWithLoading {
            cloudRepository.deleteFile(file.uniqueFilename)
                .onSuccess { message ->
                    log(message)
                    _state.value = _state.value.copy(selectedFile = null)
                    refreshFilesInternal()
                }
                .onFailure { e -> log("Delete failed: ${e.message}") }
        }
    }

    fun formatFileSize(bytes: Long): String = cloudRepository.formatSize(bytes)

    fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "Unknown"
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        val output = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (fmt in formats) {
            try {
                val parser = SimpleDateFormat(fmt, Locale.US)
                val pos = ParsePosition(0)
                val parsed = parser.parse(dateStr, pos)
                if (parsed != null && pos.index == dateStr.length) {
                    return output.format(parsed)
                }
            } catch (_: Exception) {
            }
        }
        return dateStr
    }

    // Public hook so UI (e.g. the in-app WebView login) can record progress in
    // the same status log the user can read and copy.
    fun appendLog(message: String) = log(message)

    private fun log(message: String) {
        val ts = timestampFormat.format(Date())
        val entry = "[$ts] $message"
        val current = _state.value.statusMessages.toMutableList()
        current.add(entry)
        if (current.size > 200) current.removeAt(0)
        _state.value = _state.value.copy(statusMessages = current)
    }

    private fun getFilenameFromUri(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        val segment = uri.lastPathSegment
        if (!segment.isNullOrBlank()) return segment
        return null
    }
}
