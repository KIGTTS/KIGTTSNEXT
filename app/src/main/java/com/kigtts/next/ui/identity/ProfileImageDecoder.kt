package com.kigtts.next.ui.identity

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

internal fun decodeProfileImage(resources: Resources, file: File): Drawable? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) decodeModernProfileImage(file) else decodeLegacyProfileImage(resources, file)
}.getOrNull()

@RequiresApi(28)
private fun decodeModernProfileImage(file: File): Drawable = FileInputStream(file).channel.use { channel ->
    require(channel.size() in 1..20L * 1024 * 1024)
    // Map cached immutable bytes without copying the whole GIF into the Java heap.
    val bytes = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(bytes)) { decoder, info, _ ->
        require(info.size.width in 1..16384 && info.size.height in 1..16384)
        val scale = minOf(1f, 1200f / maxOf(info.size.width, info.size.height))
        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
    }
}

@Suppress("DEPRECATION")
private fun decodeLegacyProfileImage(resources: Resources, file: File): Drawable? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    require(options.outWidth in 1..16384 && options.outHeight in 1..16384)
    // Movie cannot downsample frames. Large GIFs use a bounded still preview on Android 8.
    if (options.outMimeType == "image/gif" && options.outWidth.toLong() * options.outHeight <= 4_000_000) {
        Movie.decodeFile(file.path)?.let { return ProfileMovieDrawable(it) }
    }
    options.inJustDecodeBounds = false
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1200) options.inSampleSize *= 2
    return BitmapFactory.decodeFile(file.path, options)?.let { BitmapDrawable(resources, it) }
}

/** Android 8 GIF playback uses the same visibility/lifecycle start and stop as modern images. */
@Suppress("DEPRECATION")
private class ProfileMovieDrawable(private val movie: Movie) : Drawable(), Animatable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var running = false
    private var elapsed = 0L
    private var startedAt = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            invalidateSelf()
            scheduleSelf(this, SystemClock.uptimeMillis() + 33)
        }
    }

    override fun getIntrinsicWidth() = movie.width()
    override fun getIntrinsicHeight() = movie.height()
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val time = elapsed + if (running) SystemClock.uptimeMillis() - startedAt else 0
        movie.setTime((time % movie.duration().coerceAtLeast(100)).toInt())
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width().toFloat() / movie.width(), bounds.height().toFloat() / movie.height())
        movie.draw(canvas, 0f, 0f, paint)
        canvas.restoreToCount(saved)
    }
    override fun start() {
        if (running) return
        running = true
        startedAt = SystemClock.uptimeMillis()
        frame.run()
    }
    override fun stop() {
        if (!running) return
        elapsed += SystemClock.uptimeMillis() - startedAt
        running = false
        unscheduleSelf(frame)
    }
    override fun isRunning() = running
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
