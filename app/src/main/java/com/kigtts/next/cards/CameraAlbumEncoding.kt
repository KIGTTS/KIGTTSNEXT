package com.kigtts.next.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.kigtts.next.media.CameraPhotoEncoding
import com.kigtts.next.media.encodeCameraPhoto
import com.kigtts.plugin.api.PolaroidCard
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class CameraAlbumEncoding(private val stickers: StickerRepository) {
    private val preferences = stickers.context.getSharedPreferences("camera-photo-encoding", 0)
    // Publication is serialized by the album. Remember an encoder's large-image failure for this session.
    private var limitLargeHeif = false

    fun set(id: String, encoding: CameraPhotoEncoding) { preferences.edit().putString(id, encoding.name).apply() }
    fun get(id: String): CameraPhotoEncoding? = preferences.getString(id, null)?.let { CameraPhotoEncoding.read(it) }

    fun stage(card: PolaroidCard, encoding: CameraPhotoEncoding, original: File?): File {
        val heif = encoding == CameraPhotoEncoding.HEIF && CameraPhotoEncoding.heifAvailable
        if (original != null) {
            if (!heif || Build.VERSION.SDK_INT < 28) return original
            return encodeOriginal(original)
        }
        val bitmap = PolaroidRenderer.renderBitmap(stickers, card, maximumHeight = galleryPolaroidHeight(stickers, card))
        try {
            return (if (heif) tryHeif { write(bitmap, CameraPhotoEncoding.HEIF) } else null)
                ?: write(bitmap, CameraPhotoEncoding.JPEG)
        } finally { bitmap.recycle() }
    }

    @RequiresApi(28)
    private fun encodeOriginal(file: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val large = bounds.outWidth.toLong() * bounds.outHeight > 12_000_000 || maxOf(bounds.outWidth, bounds.outHeight) > 4096
        fun attempt(reduce: Boolean): File? = tryHeif {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                if (reduce) {
                    val (width, height) = info.size.let { it.width to it.height }
                    val scale = minOf(1.0, 4096.0 / maxOf(width, height), sqrt(12_000_000.0 / (width.toLong() * height)))
                    decoder.setTargetSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
                }
            }
            try { write(bitmap, CameraPhotoEncoding.HEIF) } finally { bitmap.recycle() }
        }
        if (!large || !limitLargeHeif) attempt(false)?.let { return it }
        if (large) attempt(true)?.let { limitLargeHeif = true; return it }
        return file
    }

    private fun tryHeif(action: () -> File): File? = try { action() }
    catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) { Log.w("KigCameraEncoding", "HEIF attempt failed", failure); null }
    catch (failure: OutOfMemoryError) { Log.w("KigCameraEncoding", "Insufficient memory for HEIF", failure); null }

    private fun write(bitmap: Bitmap, format: CameraPhotoEncoding): File {
        val file = File(stickers.context.cacheDir, "camera-${UUID.randomUUID()}.${format.extension}")
        try {
            encodeCameraPhoto(bitmap, file, format)
            if (format == CameraPhotoEncoding.HEIF) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                check(bounds.outWidth == bitmap.width && bounds.outHeight == bitmap.height &&
                    bounds.outMimeType in setOf("image/heif", "image/heic")) { "HEIF cannot be read by this device" }
            }
            return file
        } catch (error: Throwable) { file.delete(); throw error }
    }
}
