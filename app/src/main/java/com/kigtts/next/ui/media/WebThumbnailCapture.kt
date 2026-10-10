package com.kigtts.next.ui.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import com.kigtts.next.media.MediaEntry
import com.kigtts.next.media.MediaRepository
import kotlin.math.*

/** Draw an already visible WebView on the UI thread, bounded to 512px; analysis stays off-thread. */
internal fun captureWebThumbnail(view: WebView, entry: MediaEntry, repository: MediaRepository) {
    if (view.width <= 0 || view.height <= 0 || repository.get(entry.id) == null) return
    runCatching {
        val scale = min(1f, 512f / max(view.width, view.height))
        val bitmap = Bitmap.createBitmap((view.width * scale).roundToInt().coerceAtLeast(1),
            (view.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap).apply { scale(scale, scale) })
        repository.thumbnails.rememberWeb(entry, bitmap)
    }
}
