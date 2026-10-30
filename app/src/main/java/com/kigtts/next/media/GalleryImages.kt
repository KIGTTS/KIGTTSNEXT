package com.kigtts.next.media

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal suspend fun requestGalleryWrite(context: Context): Boolean = Build.VERSION.SDK_INT >= 29 ||
    WebAndroidPermissions.request(context, listOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))

internal interface GalleryImagePublication {
    fun publish(image: File, folder: String, takenAt: Long = System.currentTimeMillis()): Uri
    fun remove(uri: Uri)
    fun exists(uri: Uri): Boolean
}

/** Copies encoded bytes (including GIF animation and Motion Photo trailers) without re-encoding. */
internal class GalleryImages(private val context: Context) : GalleryImagePublication {
    override fun publish(image: File, folder: String, takenAt: Long): Uri {
        require(folder in setOf("拍立得", "图片"))
        require(image.isFile && image.length() > 0) { "图片不存在" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        val extension = when (bounds.outMimeType) {
            "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"
            "image/webp" -> "webp"; "image/heif", "image/heic" -> "heic"
            "image/avif" -> "avif"; "image/bmp", "image/x-ms-bmp" -> "bmp"
            else -> error("暂不支持保存这种图片")
        }
        val resolver = context.contentResolver
        val name = "KIGTTS-${UUID.randomUUID()}.$extension"
        var uri: Uri? = null
        var legacy: File? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, bounds.outMimeType)
                put(MediaStore.Images.Media.WIDTH, bounds.outWidth); put(MediaStore.Images.Media.HEIGHT, bounds.outHeight)
                put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KIGTTS/$folder")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "KIGTTS/$folder")
                    check(directory.isDirectory || directory.mkdirs()) { "无法创建系统相册" }
                    legacy = File(directory, name)
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, legacy!!.absolutePath)
                }
            }
            uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) { "无法创建系统相册图片" }
            requireNotNull(resolver.openOutputStream(uri, "w")) { "无法写入系统相册" }.use { output ->
                image.inputStream().use { it.copyTo(output) }; output.flush()
            }
            if (Build.VERSION.SDK_INT >= 29) check(resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null) == 1) { "无法完成系统相册保存" }
            return uri
        } catch (failure: Exception) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }; legacy?.delete()
            throw failure
        }
    }
    override fun remove(uri: Uri) {
        require(uri.scheme == "content" && uri.authority == "media" && uri.pathSegments.let {
            it.size == 4 && it[1] == "images" && it[2] == "media" && it[3].toLongOrNull() != null
        })
        context.contentResolver.delete(uri, null, null)
    }
    override fun exists(uri: Uri): Boolean = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}

internal suspend fun saveGalleryImage(context: Context, image: File): Uri {
    check(requestGalleryWrite(context)) { "未允许保存到系统相册" }
    return withContext(Dispatchers.IO) { GalleryImages(context).publish(image, "图片") }
}
