package com.kigtts.next.ui.media

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Asynchronous, downscaled readback; never wait for GPU pixels on the UI thread. */
internal suspend fun videoFrameSnapshot(texture: TextureView): Bitmap? = suspendCancellableCoroutine { result ->
    val source = texture.surfaceTexture
    if (source == null || !texture.isAvailable || texture.width <= 0 || texture.height <= 0) { result.resume(null); return@suspendCancellableCoroutine }
    val factor = 128f / maxOf(texture.width,texture.height)
    val bitmap = Bitmap.createBitmap((texture.width*factor).toInt().coerceAtLeast(1), (texture.height*factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val surface = Surface(source)
    try {
        PixelCopy.request(surface, bitmap, { status ->
            surface.release()
            if (result.isActive && status == PixelCopy.SUCCESS) result.resume(bitmap)
            else { bitmap.recycle(); if (result.isActive) result.resume(null) }
        }, Handler(Looper.getMainLooper()))
    } catch (_: Exception) { surface.release(); bitmap.recycle(); if (result.isActive) result.resume(null) }
}
