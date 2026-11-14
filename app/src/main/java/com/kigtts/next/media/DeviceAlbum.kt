package com.kigtts.next.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class AlbumMedia(val uri: Uri, val name: String, val mime: String, val takenAt: Long,
    val width: Int, val height: Int, val duration: Long = 0, val own: Boolean = false,
    val bucketId: String = "", val bucketName: String = "", val favorite: Boolean = false, val addedAt: Long = takenAt) {
    val video get() = mime.startsWith("video/")
    val day get() = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** Only lightweight metadata is indexed. Thumbnails are requested lazily for visible cells. */
internal class DeviceAlbum(private val context: Context) {
    val changes = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        context.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
    suspend fun load(imagesOnly: Boolean = false, videosOnly: Boolean = false): List<AlbumMedia> = withContext(Dispatchers.IO) {
        val columns = listOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION) + if (Build.VERSION.SDK_INT >= 29)
            (listOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.OWNER_PACKAGE_NAME, MediaStore.MediaColumns.VOLUME_NAME) +
                if (Build.VERSION.SDK_INT >= 30) listOf(MediaStore.MediaColumns.IS_FAVORITE) else emptyList())
        else listOf(MediaStore.MediaColumns.DATA)
        val result = ArrayList<AlbumMedia>()
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        val images = "$type = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
        val videos = "$type = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
        val mediaTypes = if (imagesOnly) images else if (videosOnly) videos else "$images OR $videos"
        context.contentResolver.query(MediaStore.Files.getContentUri("external"), columns.toTypedArray(),
            "($mediaTypes)" + if (Build.VERSION.SDK_INT >= 29) " AND ${MediaStore.MediaColumns.IS_PENDING} = 0" else "", null, "${MediaStore.MediaColumns._ID} DESC")?.use { cursor ->
            fun str(name: String) = cursor.getColumnIndex(name).let { if (it < 0 || cursor.isNull(it)) "" else cursor.getString(it) }
            fun long(name: String) = cursor.getColumnIndex(name).let { if (it < 0 || cursor.isNull(it)) 0L else cursor.getLong(it) }
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val mime = str(MediaStore.MediaColumns.MIME_TYPE)
                if (!mime.startsWith("image/") && !mime.startsWith("video/")) continue
                val collection = if (mime.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val path = if (Build.VERSION.SDK_INT >= 29) str(MediaStore.MediaColumns.RELATIVE_PATH) else str(MediaStore.MediaColumns.DATA)
                val folder = if (Build.VERSION.SDK_INT >= 29) path.trimEnd('/') else path.substringBeforeLast('/', "")
                val added = long(MediaStore.MediaColumns.DATE_ADDED) * 1000
                result += AlbumMedia(ContentUris.withAppendedId(collection, long(MediaStore.MediaColumns._ID)),
                    str(MediaStore.MediaColumns.DISPLAY_NAME), mime,
                    long(MediaStore.Images.ImageColumns.DATE_TAKEN).takeIf { it > 0 } ?: added,
                    long(MediaStore.MediaColumns.WIDTH).toInt(), long(MediaStore.MediaColumns.HEIGHT).toInt(),
                    long(MediaStore.Video.VideoColumns.DURATION), path.contains("/KIGTTS/", true) || path.startsWith("KIGTTS/") ||
                        Build.VERSION.SDK_INT >= 29 && str(MediaStore.MediaColumns.OWNER_PACKAGE_NAME) == context.packageName,
                    bucketId = if (Build.VERSION.SDK_INT >= 29) "${str(MediaStore.MediaColumns.VOLUME_NAME)}/$folder" else folder,
                    bucketName = folder.substringAfterLast('/'),
                    favorite = Build.VERSION.SDK_INT >= 30 && long(MediaStore.MediaColumns.IS_FAVORITE) == 1L,
                    addedAt = added)
            }
        }
        result.sortedWith(compareByDescending<AlbumMedia> { it.takenAt }.thenByDescending { it.uri.lastPathSegment?.toLongOrNull() ?: 0 })
    }
    companion object {
        fun permissions(imagesOnly: Boolean = false): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> (listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) +
                if (imagesOnly) emptyList() else listOf(Manifest.permission.READ_MEDIA_VIDEO)).toTypedArray()
            Build.VERSION.SDK_INT >= 33 -> if (imagesOnly) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        fun canRead(context: Context, imagesOnly: Boolean = false) = permissions(imagesOnly).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
