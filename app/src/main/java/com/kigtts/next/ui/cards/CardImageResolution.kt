package com.kigtts.next.ui.cards

/** Decode in buckets, using the photo's longest displayed edge rather than the paper width. */
internal fun cardImageDecodeSize(pixels: Float, maximum: Int): Int {
    val target = pixels.takeIf { it.isFinite() }?.coerceAtLeast(1f) ?: 1f
    return minOf(maximum, listOf(128, 256, 512, 1024, 1536, 2048, 3072, 4096)
        .firstOrNull { it >= target } ?: maximum)
}

internal fun cardPhotoDecodeSize(width: Float, height: Float, photoZoom: Double, viewScale: Float, maximum: Int): Int =
    cardImageDecodeSize(maxOf(width, height) * photoZoom.toFloat().coerceIn(1f, 8f) * viewScale.coerceIn(1f, 4f), maximum)
