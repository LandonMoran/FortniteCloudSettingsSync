package com.fortnitecloudsync

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fortnitecloudsync.ui.FilesScreen
import com.fortnitecloudsync.ui.LoginScreen
import com.fortnitecloudsync.ui.MainViewModel
import com.fortnitecloudsync.ui.WebLoginScreen
import com.fortnitecloudsync.ui.theme.FortniteCloudTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FortniteCloudTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    var showWebLogin by remember { mutableStateOf(false) }

                    val uploadLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenMultipleDocuments()
                    ) { uris: List<Uri> ->
                        if (uris.isNotEmpty()) viewModel.uploadFiles(this@MainActivity, uris)
                    }

                    if (showWebLogin && !state.isAuthenticated) {
                        WebLoginScreen(
                            authUrl = viewModel.getAuthorizationUrl(),
                            onCodeCaptured = { captured ->
                                showWebLogin = false
                                viewModel.authenticate(captured)
                            },
                            onCancel = { showWebLogin = false }
                        )
                    } else if (!state.isAuthenticated) {
                        LoginScreen(
                            onLogin = { viewModel.getAuthorizationUrl() },
                            onWebLogin = { showWebLogin = true },
                            onAuthenticate = { code -> viewModel.authenticate(code) },
                            isLoading = state.isLoading,
                            statusMessages = state.statusMessages
                        )
                    } else {
                        FilesScreen(
                            state = state,
                            onRefresh = { viewModel.refreshFiles() },
                            onFilterToggle = { viewModel.toggleFilter(it) },
                            onSelectFile = { viewModel.selectFile(it) },
                            onDownload = { file -> viewModel.downloadFile(this@MainActivity, file) },
                            onDownloadAll = { viewModel.downloadAllFiles(this@MainActivity) },
                            onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                            onDelete = { file -> viewModel.deleteFile(file) },
                            onLogout = { viewModel.logout() },
                            formatSize = { viewModel.formatFileSize(it) },
                            formatDate = { viewModel.formatDate(it) }
                        )
                    }
                }
            }
        }
    }
}
