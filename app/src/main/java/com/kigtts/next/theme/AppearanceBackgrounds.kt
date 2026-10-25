package com.kigtts.next.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

enum class BackgroundBlur(val sigma: Float) { NONE(0f), PAGE(3f), CARD(9f) }

/** Process-wide, orientation-independent images. Eviction never recycles a bitmap still drawn by a page. */
internal object AppearanceBackgrounds {
    private val cache = object : LruCache<String, Bitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private fun key(context: Context, asset: String?, blur: BackgroundBlur) = context.filesDir.path + "/" + asset + "/" + blur
    fun peek(context: Context, asset: String?, blurred: Boolean = false): Bitmap? = peek(context, asset, if (blurred) BackgroundBlur.CARD else BackgroundBlur.NONE)
    fun peek(context: Context, asset: String?, blur: BackgroundBlur): Bitmap? = cache.get(key(context, asset, blur))
    fun load(context: Context, asset: String?, blurred: Boolean = false): Bitmap? = load(context, asset, if (blurred) BackgroundBlur.CARD else BackgroundBlur.NONE)
    @Synchronized fun load(context: Context, asset: String?, blur: BackgroundBlur): Bitmap? {
        if (asset == null) return null
        peek(context, asset, blur)?.let { return it }
        val file = if (blur != BackgroundBlur.NONE) BackgroundBlurCache(context).file(asset, blur) else AppearanceAssets(context).file(asset)
        return file?.let { BitmapFactory.decodeFile(it.path) }?.also { cache.put(key(context, asset, blur), it) }
    }
}
