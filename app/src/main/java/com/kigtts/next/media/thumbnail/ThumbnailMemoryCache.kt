package com.kigtts.next.media.thumbnail

import android.graphics.Bitmap
import android.util.LruCache

/** Evicted pixels may still be on screen: never recycle a bitmap handed to a consumer. */
internal class ThumbnailMemoryCache(maxBytes: Int) : LruCache<String, Bitmap>(maxBytes) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}
