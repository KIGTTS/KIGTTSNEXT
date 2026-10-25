package com.kigtts.next.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/** Process-wide, orientation-independent images. Eviction never recycles a bitmap still drawn by a page. */
internal object AppearanceBackgrounds {
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private fun key(context: Context, asset: String?, blurred: Boolean) = context.filesDir.path + "/" + asset + "/" + blurred
    fun peek(context: Context, asset: String?, blurred: Boolean = false): Bitmap? = cache.get(key(context, asset, blurred))
    @Synchronized fun load(context: Context, asset: String?, blurred: Boolean = false): Bitmap? {
        if (asset == null) return null
        peek(context, asset, blurred)?.let { return it }
        val file = if (blurred) BackgroundBlurCache(context).file(asset) else AppearanceAssets(context).file(asset)
        return file?.let { BitmapFactory.decodeFile(it.path) }?.also { cache.put(key(context, asset, blurred), it) }
    }
}
