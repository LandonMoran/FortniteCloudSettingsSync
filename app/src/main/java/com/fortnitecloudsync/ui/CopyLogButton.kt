package com.fortnitecloudsync.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Copies the full status log to the clipboard. Shown on every screen that has a
 * status log so debug output (especially authentication errors on the login
 * screen) can always be copied and shared.
 */
@Composable
fun CopyLogButton(
    messages: List<String>,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty()) return
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }
    OutlinedButton(
        onClick = {
            val fullLog = messages.joinToString("\n")
            clipboardManager?.setPrimaryClip(
                ClipData.newPlainText("Fortnite Sync Log", fullLog)
            )
            Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
        },
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "Copy log to clipboard"
                role = Role.Button
            },
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            Icons.Default.ContentCopy,
            contentDescription = "Copy",
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Copy Log", fontSize = 13.sp)
    }
}
