package com.kigtts.next.media

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal object WebDownloads {
    fun name(value: String) = value.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\p{Cntrl}<>:\"|?*]"), "_").trim().take(100).ifBlank { "KIGTTS-download" }
    fun remote(context: Context, url: String, disposition: String?, mime: String?) {
        require(WebContentPolicy.https(url) && java.net.URI(url).userInfo == null)
        val request = DownloadManager.Request(Uri.parse(url)).setTitle(name(URLUtil.guessFileName(url, disposition, mime)))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "KIGTTS-${UUID.randomUUID().toString().take(8)}-${name(URLUtil.guessFileName(url, disposition, mime))}")
        mime?.takeIf { it.length < 120 }?.let(request::setMimeType)
        context.getSystemService(DownloadManager::class.java).enqueue(request)
    }
    suspend fun data(context: Context, filename: String, mime: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(bytes.size <= 8 * 1024 * 1024 && mime.length in 1..120)
        val safeName = "KIGTTS-${UUID.randomUUID().toString().take(8)}-${name(filename)}"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, safeName); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KIGTTS"); put(MediaStore.Downloads.IS_PENDING, 1) }
            val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            try { context.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(bytes) }; context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) }
            catch (failure: Exception) { context.contentResolver.delete(uri, null, null); throw failure }
        } else {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            check(directory.isDirectory || directory.mkdirs()); File(directory, safeName).outputStream().use { it.write(bytes) }
        }
    }
}
