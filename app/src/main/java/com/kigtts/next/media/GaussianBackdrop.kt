package com.kigtts.next.media

import android.graphics.Bitmap
import kotlin.math.exp

/** Small separable Gaussian, computed off-thread; works on API26 without RenderScript or full-size copies. */
object GaussianBackdrop {
    fun blur(source: Bitmap, maximum: Int = 128, sigma: Float = 5f): Bitmap {
        require(maximum in 16..256 && sigma in 1f..12f)
        val factor = minOf(1f, maximum.toFloat() / maxOf(source.width, source.height))
        val width = (source.width * factor).toInt().coerceAtLeast(1); val height = (source.height * factor).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, width, height, true)
        val pixels = IntArray(width * height); small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small != source) small.recycle()
        val radius = (sigma * 3).toInt(); val kernel = FloatArray(radius * 2 + 1) { index -> exp(-((index-radius)*(index-radius)).toFloat() / (2 * sigma * sigma)) }
        val weight = kernel.sum(); kernel.indices.forEach { kernel[it] /= weight }
        val horizontal = FloatArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val offset = (y * width + x) * 4
            for (k in -radius..radius) {
                val pixel = pixels[y * width + (x+k).coerceIn(0,width-1)]; val value = kernel[k+radius]
                for (channel in 0..3) horizontal[offset+channel] += (pixel ushr (channel*8) and 255) * value
            }
        }
        val output = IntArray(pixels.size)
        for (y in 0 until height) for (x in 0 until width) {
            var pixel = 0
            for (channel in 0..3) {
                var color = 0f
                for (k in -radius..radius) color += horizontal[(((y+k).coerceIn(0,height-1))*width+x)*4+channel] * kernel[k+radius]
                pixel = pixel or (kotlin.math.round(color).toInt().coerceIn(0,255) shl (channel*8))
            }
            output[y * width + x] = pixel
        }
        return Bitmap.createBitmap(output,width,height,Bitmap.Config.ARGB_8888)
    }
}
