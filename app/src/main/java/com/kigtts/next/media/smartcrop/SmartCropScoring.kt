/* Adapted from smartcrop.js (MIT), Jonas Wagner. */
package com.kigtts.next.media.smartcrop

import kotlin.math.*

internal object SmartCropScoring {
    fun score(image: CropImage, factor: Int, left: Int, top: Int, width: Double, height: Double, o: SmartCropOptions): CropScore {
        var detail = 0.0; var skin = 0.0; var saturation = 0.0; var boost = 0.0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val color = image.pixels[y * image.width + x]
            val importance = importance(x * factor, y * factor, left, top, width, height, o)
            val d = ((color ushr 8) and 255) / 255.0
            skin += ((color ushr 16) and 255) / 255.0 * (d + o.skinBias) * importance
            detail += d * importance
            saturation += (color and 255) / 255.0 * (d + o.saturationBias) * importance
            boost += (color ushr 24) / 255.0 * importance
        }
        return CropScore(detail, skin, saturation, boost,
            (detail * o.detailWeight + skin * o.skinWeight + saturation * o.saturationWeight + boost * o.boostWeight) / (width * height))
    }
    private fun importance(x: Int, y: Int, left: Int, top: Int, width: Double, height: Double, o: SmartCropOptions): Double {
        if (left > x || x >= left + width || top > y || y >= top + height) return o.outsideImportance
        val nx = (x - left) / width; val ny = (y - top) / height
        fun distance(value: Double, focus: Double) = if (focus == .5) abs(.5 - value) * 2
            else if (value < focus) (focus - value) / focus else (value - focus) / (1 - focus)
        val px = distance(nx, o.focusX); val py = distance(ny, o.focusY)
        val dx = max(px - 1 + o.edgeRadius, 0.0); val dy = max(py - 1 + o.edgeRadius, 0.0)
        val edge = (dx * dx + dy * dy) * o.edgeWeight
        var center = 1.41 - sqrt(px * px + py * py)
        if (o.ruleOfThirds) center += max(0.0, center + edge + .5) * 1.2 * (thirds(px) + thirds(py))
        return center + edge
    }
    private fun thirds(value: Double): Double {
        val x = (((value - 1.0 / 3 + 1) % 2) * .5 - .5) * 16
        return max(1 - x * x, 0.0)
    }
}
