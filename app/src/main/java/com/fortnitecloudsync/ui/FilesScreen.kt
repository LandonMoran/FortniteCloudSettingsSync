package com.fortnitecloudsync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fortnitecloudsync.data.model.CloudFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    state: AppState,
    onRefresh: () -> Unit,
    onFilterToggle: (Boolean) -> Unit,
    onSelectFile: (CloudFile?) -> Unit,
    onDownload: (CloudFile) -> Unit,
    onDownloadAll: () -> Unit,
    onUpload: () -> Unit,
    onDelete: (CloudFile) -> Unit,
    onLogout: () -> Unit,
    formatSize: (Long) -> String,
    formatDate: (String?) -> String
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val logListState = rememberLazyListState()

    LaunchedEffect(state.statusMessages.size) {
        if (state.statusMessages.isNotEmpty()) {
            logListState.animateScrollToItem(state.statusMessages.size - 1)
        }
    }

    if (showDeleteDialog && state.selectedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirm Deletion") },
            text = {
                Text(
                    "Are you sure you want to delete '${state.selectedFile.uniqueFilename}'?\n" +
                    "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(state.selectedFile)
                        showDeleteDialog = false
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fortnite Cloud Sync")
                        state.displayName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(
                        onClick = { state.selectedFile?.let { onDownload(it) } },
                        enabled = state.selectedFile != null && !state.isLoading
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download selected")
                    }
                    IconButton(
                        onClick = onDownloadAll,
                        enabled = state.cloudFiles.isNotEmpty() && !state.isLoading
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Download all")
                    }
                    IconButton(
                        onClick = { state.selectedFile?.let { showDeleteDialog = true } },
                        enabled = state.selectedFile != null && !state.isLoading
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete selected",
                            tint = if (state.selectedFile != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = { if (!state.isLoading) onUpload() }) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload files")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Filter toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Hide restricted files", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Filters Switch/UUID files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.filterRestricted,
                    onCheckedChange = onFilterToggle
                )
            }

            Divider()

            // File count header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.cloudFiles.size} file${if (state.cloudFiles.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.selectedFile?.let {
                    Text(
                        text = "Selected: ${it.uniqueFilename}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }

            // Files list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.cloudFiles.isEmpty() && !state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "No files found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(state.cloudFiles, key = { it.uniqueFilename }) { file ->
                    val isSelected = state.selectedFile == file
                    FileRow(
                        file = file,
                        isSelected = isSelected,
                        formatSize = formatSize,
                        formatDate = formatDate,
                        onClick = { onSelectFile(if (isSelected) null else file) }
                    )
                }
            }

            Divider()

            // Status log
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Status Log",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyColumn(state = logListState) {
                        items(state.statusMessages) { message ->
                            Text(
                                text = message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FileRow(
    file: CloudFile,
    isSelected: Boolean,
    formatSize: (Long) -> String,
    formatDate: (String?) -> String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilePresent,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.uniqueFilename,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatSize(file.length),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(file.lastModified),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
