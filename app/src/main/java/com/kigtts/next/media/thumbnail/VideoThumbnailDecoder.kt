package com.kigtts.next.media.thumbnail

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import java.io.File
import kotlin.math.*

internal object VideoThumbnailDecoder {
    fun decode(file: File, maximum: Int = 384): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val time = min(1000L, duration / 2) * 1000
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: maximum
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: maximum
            val factor = max(1.0, max(w, h).toDouble() / maximum)
            val targetW = (w / factor).toInt().coerceAtLeast(1); val targetH = (h / factor).toInt().coerceAtLeast(1)
            fun frame(at: Long): Bitmap? = when {
                Build.VERSION.SDK_INT >= 30 -> retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH,
                    MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 })
                Build.VERSION.SDK_INT >= 27 -> retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetW, targetH)
                else -> retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            val decoded = frame(time) ?: frame(0) ?: return null
            val bitmap = if (decoded.config == Bitmap.Config.HARDWARE) requireNotNull(decoded.copy(Bitmap.Config.ARGB_8888, false)).also { decoded.recycle() } else decoded
            val scale = min(1.0, maximum.toDouble() / max(bitmap.width, bitmap.height))
            return if (scale >= 1) bitmap else Bitmap.createScaledBitmap(bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } finally { retriever.release() }
    }
}
