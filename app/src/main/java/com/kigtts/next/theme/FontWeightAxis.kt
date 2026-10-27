package com.kigtts.next.theme

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

data class FontWeightAxis(val min: Float, val default: Float, val max: Float) {
    fun clamp(weight: Int) = weight.toFloat().coerceIn(min, max).roundToInt()
    fun forStyle(weight: Int, style: Int) = (clamp(weight) + style - 400f).coerceIn(min, max)
}

/** Read only the bounded sfnt directory/fvar axis records, never the glyph payload. */
internal fun readFontWeightAxis(file: File): FontWeightAxis? = runCatching {
    RandomAccessFile(file, "r").use { input ->
        if (input.length() < 12) return@use null
        val signature = input.readInt()
        if (signature != 0x00010000 && signature != 0x4f54544f) return@use null
        val count = input.readUnsignedShort()
        if (count !in 1..256 || 12L + count * 16L > input.length()) return@use null
        input.skipBytes(6)
        var offset = 0L; var length = 0L
        repeat(count) {
            val tag = input.readInt(); input.readInt()
            val at = input.readInt().toLong() and 0xffffffffL
            val size = input.readInt().toLong() and 0xffffffffL
            if (tag == 0x66766172) { offset = at; length = size }
        }
        if (length < 16 || offset < 12 || offset + length > input.length()) return@use null
        input.seek(offset)
        if (input.readInt() != 0x00010000) return@use null
        val axesOffset = input.readUnsignedShort(); input.readUnsignedShort()
        val axesCount = input.readUnsignedShort(); val axisSize = input.readUnsignedShort()
        if (axesOffset < 16 || axesCount !in 1..64 || axisSize < 20 || axesOffset.toLong() + axesCount * axisSize > length) return@use null
        repeat(axesCount) { index ->
            input.seek(offset + axesOffset + index * axisSize)
            if (input.readInt() == 0x77676874) {
                val min = input.readInt() / 65536f; val normal = input.readInt() / 65536f; val max = input.readInt() / 65536f
                return@use if (min >= 1 && max <= 1000 && min < max && normal in min..max) FontWeightAxis(min, normal, max) else null
            }
        }
        null
    }
}.getOrNull()
