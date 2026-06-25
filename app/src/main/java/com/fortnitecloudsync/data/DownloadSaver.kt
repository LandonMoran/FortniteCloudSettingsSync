package com.fortnitecloudsync.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Saves downloaded files to the public Downloads/FortniteCloudSync folder so the
 * user can actually find, open, and share them (the app-private external dir is
 * hidden by the Files app on Android 11+).
 */
object DownloadSaver {

    private const val SUBDIR = "FortniteCloudSync"

    /** Returns a friendly location string on success, e.g. "Downloads/FortniteCloudSync/<name>". */
    fun save(context: Context, filename: String, bytes: ByteArray): Result<String> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, filename, bytes)
        } else {
            saveViaLegacyFile(filename, bytes)
        }
        "Downloads/$SUBDIR/$filename"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, filename: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIR

        // Replace an existing same-named file so re-downloads don't pile up duplicates.
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(filename, "$relativePath/"),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Could not create Downloads entry")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not open output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyFile(filename: String, bytes: ByteArray) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            SUBDIR
        )
        dir.mkdirs()
        File(dir, filename).writeBytes(bytes)
    }
}
