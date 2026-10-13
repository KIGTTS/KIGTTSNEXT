package com.kigtts.next.ui.sketch

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

internal interface SketchPublication {
    fun publish(image: File, name: String, width: Int, height: Int): Uri
    fun remove(uri: Uri)
}

/** Never expose a partially written image in the system gallery. */
internal class SketchMediaPublisher(private val context: Context) : SketchPublication {
    override fun publish(image: File, name: String, width: Int, height: Int): Uri {
        val resolver = context.contentResolver
        var uri: Uri? = null
        var legacyFile: File? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.WIDTH, width); put(MediaStore.Images.Media.HEIGHT, height)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KIGTTS/画板")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "KIGTTS/画板")
                    check(directory.isDirectory || directory.mkdirs()) { "无法创建系统相册" }
                    legacyFile = File(directory, name)
                    @Suppress("DEPRECATION")
                    put(MediaStore.Images.Media.DATA, legacyFile!!.absolutePath)
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
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            legacyFile?.delete()
            throw failure
        }
    }
    override fun remove(uri: Uri) {
        require(uri.scheme == "content" && uri.authority == "media" && uri.pathSegments.let {
            it.size == 4 && it[1] == "images" && it[2] == "media" && it[3].toLongOrNull() != null
        }) { "只能移除画板保存的图片" }
        context.contentResolver.delete(uri, null, null)
    }
}
