package com.kigtts.next.identity

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.theme.BackgroundCrop
import com.kigtts.next.theme.cropBackground
import java.io.File

/** Keep the picked source temporary; only a confirmed square enters the shared image catalog. */
internal fun prepareAvatarSource(context: Context, uri: Uri): File {
    val file = File.createTempFile("avatar-source-", ".image", context.cacheDir)
    try {
        requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> file.outputStream().use { output ->
            val buffer = ByteArray(65536); var bytes = 0L
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                bytes += count; require(bytes <= 32L * 1024 * 1024) { "请选择小于32MB的图片" }
                output.write(buffer, 0, count)
            }
            require(bytes > 0) { "这张图片是空的" }
        } }
        return file
    } catch (error: Exception) { file.delete(); throw error }
}

internal fun saveAvatarCrop(stickers: StickerRepository, bitmap: Bitmap, crop: BackgroundCrop): String {
    val square = cropBackground(bitmap, crop)
    val output = if (square.width > 1024) Bitmap.createScaledBitmap(square, 1024, 1024, true) else square
    val file = File.createTempFile("avatar-crop-", ".png", stickers.context.cacheDir)
    try {
        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return stickers.importResource(file).id
    } finally { file.delete(); if (output !== square) output.recycle(); square.recycle() }
}
