package com.kigtts.next.ui.cards

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.content.ContentResolver
import kotlinx.coroutines.*

/** A full OEM camera has no result contract. Only its new DCIM photographs may be received automatically. */
internal object SystemCameraPhotos {
    val permission: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    private val images get() = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    fun newestId(context: Context): Long = context.contentResolver.query(images, arrayOf(MediaStore.Images.Media._ID),
        query(limit = 1), null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
    private fun query(limit: Int) = Bundle().apply {
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media._ID))
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
    }
    internal fun eligible(id: Long, previousId: Long, addedSeconds: Long, takenMillis: Long, started: Long, path: String): Boolean {
        val normalized = path.replace('\\', '/').lowercase()
        return id > previousId && addedSeconds >= started / 1000 - 1 &&
            (takenMillis <= 0 || takenMillis >= started - 2000) && normalized.contains("dcim/") &&
            !normalized.contains("screenshot") && !normalized.contains("screenrecord")
    }
    private fun newest(context: Context, previousId: Long, started: Long): Uri? {
        val path = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val columns = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_TAKEN, path)
        val args = query(16).apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Images.Media._ID} > ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(previousId.toString(), (started / 1000 - 1).toString()))
        }
        return context.contentResolver.query(images, columns, args, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                if (eligible(id, previousId, cursor.getLong(1), cursor.getLong(2), started, cursor.getString(3).orEmpty()))
                    return@use android.content.ContentUris.withAppendedId(images, id)
            }
            null
        }
    }
    suspend fun receive(context: Context, previousId: Long, started: Long): Uri? = withContext(Dispatchers.IO) {
        repeat(12) {
            newest(context, previousId, started)?.let { return@withContext it }
            delay(200)
        }
        null
    }
}
