package com.kigtts.next.media.thumbnail

import com.kigtts.next.media.*
import com.kigtts.next.media.smartcrop.*
import kotlin.math.*

data class ThumbnailRegion(val left: Int, val top: Int, val width: Int, val height: Int, val fit: Boolean)

/** Content heuristics only. No face detection, skin bias, OCR, training model or semantic claim. */
object ThumbnailFraming {
    fun region(image: CropImage, aspectWidth: Int, aspectHeight: Int, entry: MediaEntry, check: () -> Unit = {}): ThumbnailRegion {
        val bounds = alphaBounds(image)
        val input = CropImage(bounds.width, bounds.height, IntArray(bounds.width * bounds.height) { i ->
            val c = image.pixels[(i / bounds.width + bounds.top) * image.width + i % bounds.width + bounds.left]
            val alpha = c ushr 24
            fun channel(shift: Int) = (((c ushr shift) and 255) * alpha + 128 * (255 - alpha)) / 255
            (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        })
        if (entry.thumbnailMode == ThumbnailMode.FIT || entry.kind in setOf(MediaKind.HTML, MediaKind.WEB) && entry.thumbnailMode == ThumbnailMode.AUTO ||
            bounds.fit && entry.thumbnailMode == ThumbnailMode.AUTO) return bounds.copy(fit = true)
        if (entry.thumbnailMode == ThumbnailMode.FOCUS) {
            val scale = min(image.width.toDouble() / aspectWidth, image.height.toDouble() / aspectHeight)
            val w = (aspectWidth * scale).toInt().coerceIn(1, image.width)
            val h = (aspectHeight * scale).toInt().coerceIn(1, image.height)
            return ThumbnailRegion((entry.focusX * image.width - w * .65).roundToInt().coerceIn(0, image.width - w),
                (entry.focusY * image.height - h * .5).roundToInt().coerceIn(0, image.height - h), w, h, false)
        }
        val crop = SmartCrop.crop(input, aspectWidth, aspectHeight,
            SmartCropOptions(skinWeight = 0.0, focusX = .65, ruleOfThirds = false, prescale = false), check)
        // Conservative preservation: widely distributed detail that would be lost uses the entire content.
        var all = 0.0; var lost = 0.0
        var minX = input.width; var maxX = 0; var minY = input.height; var maxY = 0
        for (y in 1 until input.height step 2) {
            check()
            for (x in 1 until input.width step 2) {
                fun channel(c: Int, shift: Int) = (c ushr shift) and 255
                val c = input.pixels[y * input.width + x]
                val left = input.pixels[y * input.width + x - 1]; val above = input.pixels[(y - 1) * input.width + x]
                val detail = intArrayOf(16, 8, 0).sumOf { abs(channel(c, it) - channel(left, it)) + abs(channel(c, it) - channel(above, it)) }
                if (detail < 48) continue
                all += detail
                if (x < crop.x || x >= crop.x + crop.width || y < crop.y || y >= crop.y + crop.height) lost += detail
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
            }
        }
        if (all > 0 && lost / all > .22 && (maxX - minX > input.width * .7 || maxY - minY > input.height * .7)) return bounds.copy(fit = true)
        return ThumbnailRegion(bounds.left + crop.x, bounds.top + crop.y, crop.width, crop.height, false)
    }

    /** Ignore hidden RGB and trim transparent padding, retaining 6% breathing room. */
    private fun alphaBounds(image: CropImage): ThumbnailRegion {
        var transparent = 0; var left = image.width; var top = image.height; var right = -1; var bottom = -1
        image.pixels.forEachIndexed { i, c ->
            if (c ushr 24 < 240) transparent++
            if (c ushr 24 > 16) { val x = i % image.width; val y = i / image.width
                left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y) }
        }
        if (right < left || transparent < image.pixels.size * .02) return ThumbnailRegion(0, 0, image.width, image.height, false)
        val padX = max(1, ((right - left + 1) * .06).roundToInt()); val padY = max(1, ((bottom - top + 1) * .06).roundToInt())
        left = max(0, left - padX); right = min(image.width - 1, right + padX)
        top = max(0, top - padY); bottom = min(image.height - 1, bottom + padY)
        // Full content is safest for transparent stickers: trimming is useful without chopping the expression.
        return ThumbnailRegion(left, top, right - left + 1, bottom - top + 1, true)
    }
}
