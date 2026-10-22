package com.kigtts.next.ui.plugins

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kigtts.next.media.UiIcons

/** Decode locally on the installer's IO worker. SVG uses the host's restricted vector renderer. */
internal fun packageIconBitmap(path: String, bytes: ByteArray): Bitmap? = runCatching {
    require(bytes.size in 1..1_048_576)
    if (path.endsWith(".svg", true)) UiIcons.renderSvg(bytes.toString(Charsets.UTF_8), 256)
    else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, (maxOf(bounds.outWidth, bounds.outHeight) + 255) / 256)
        })
    }
}.getOrNull()
