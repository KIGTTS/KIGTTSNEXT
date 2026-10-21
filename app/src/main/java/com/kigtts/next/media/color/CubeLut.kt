package com.kigtts.next.media.color

import android.graphics.Bitmap
import java.io.Reader
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Standard .cube 3D LUT, red varying fastest. Input and output are encoded RGB. */
class CubeLut internal constructor(val title: String, val size: Int, val domainMin: FloatArray, val domainMax: FloatArray,
    private val samples: FloatArray) {
    val tiles: Int = ceil(sqrt(size.toDouble())).toInt()
    fun map(color: Int, strength: Float): Int {
        val weight = strength.coerceIn(0f, 1f)
        if (weight == 0f) return color
        val red = coordinate(color ushr 16 and 255, 0); val green = coordinate(color ushr 8 and 255, 1); val blue = coordinate(color and 255, 2)
        val r0 = floor(red).toInt(); val g0 = floor(green).toInt(); val b0 = floor(blue).toInt()
        val r1 = minOf(r0 + 1, size - 1); val g1 = minOf(g0 + 1, size - 1); val b1 = minOf(b0 + 1, size - 1)
        val rf = red - r0; val gf = green - g0; val bf = blue - b0
        var result = color and -0x1000000
        for (channel in 0..2) {
            val low = mix(mix(sample(r0, g0, b0, channel), sample(r1, g0, b0, channel), rf), mix(sample(r0, g1, b0, channel), sample(r1, g1, b0, channel), rf), gf)
            val high = mix(mix(sample(r0, g0, b1, channel), sample(r1, g0, b1, channel), rf), mix(sample(r0, g1, b1, channel), sample(r1, g1, b1, channel), rf), gf)
            val mapped = mix(low, high, bf)
            val original = (color ushr (16 - channel * 8) and 255) / 255f
            val value = ((original + (mapped - original) * weight).coerceIn(0f, 1f) * 255).roundToInt()
            result = result or (value shl (16 - channel * 8))
        }
        return result
    }
    private fun coordinate(value: Int, channel: Int) = ((value / 255f - domainMin[channel]) / (domainMax[channel] - domainMin[channel])).coerceIn(0f, 1f) * (size - 1)
    private fun sample(r: Int, g: Int, b: Int, channel: Int) = samples[((b * size + g) * size + r) * 3 + channel]
    private fun mix(a: Float, b: Float, fraction: Float) = a + (b - a) * fraction
    fun writeBinary(file: java.io.File) = java.io.DataOutputStream(file.outputStream().buffered()).use { out ->
        out.writeUTF("KIGLUT1"); out.writeUTF(title); out.writeInt(size)
        domainMin.forEach(out::writeFloat); domainMax.forEach(out::writeFloat); samples.forEach(out::writeFloat)
    }
    fun atlas(): Bitmap {
        val pixels = IntArray(tiles * size * tiles * size)
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            val index = ((b * size + g) * size + r) * 3
            val x = b % tiles * size + r; val y = b / tiles * size + g
            pixels[y * tiles * size + x] = -0x1000000 or
                ((samples[index].coerceIn(0f, 1f) * 255).roundToInt() shl 16) or
                ((samples[index + 1].coerceIn(0f, 1f) * 255).roundToInt() shl 8) or
                (samples[index + 2].coerceIn(0f, 1f) * 255).roundToInt()
        }
        return Bitmap.createBitmap(pixels, tiles * size, tiles * size, Bitmap.Config.ARGB_8888)
    }
    companion object {
        const val MAX_BYTES = 16L * 1024 * 1024
        fun readBinary(file: java.io.File): CubeLut {
            require(file.length() in 1..MAX_BYTES)
            return java.io.DataInputStream(file.inputStream().buffered()).use { input ->
                require(input.readUTF() == "KIGLUT1"); val title = input.readUTF().take(80); val size = input.readInt(); require(size in 2..65)
                val minimum = FloatArray(3) { input.readFloat() }; val maximum = FloatArray(3) { input.readFloat() }
                require((0..2).all { minimum[it].isFinite() && maximum[it].isFinite() && maximum[it] > minimum[it] })
                val samples = FloatArray(size * size * size * 3) { input.readFloat().also { value -> require(value.isFinite() && value in 0f..1f) } }
                require(input.read() == -1)
                CubeLut(title, size, minimum, maximum, samples)
            }
        }
        fun read(reader: Reader, fallbackTitle: String = "自定义 LUT"): CubeLut {
            var title = fallbackTitle; var size = 0; var count = 0; var bytes = 0L
            var minimum = floatArrayOf(0f, 0f, 0f); var maximum = floatArrayOf(1f, 1f, 1f)
            var samples: FloatArray? = null
            reader.buffered().lineSequence().forEach { raw ->
                bytes += raw.length + 1; require(bytes <= MAX_BYTES) { "LUT 文件过大" }
                val line = raw.substringBefore('#').trim().removePrefix("\uFEFF")
                if (line.isEmpty()) return@forEach
                val fields = line.split(Regex("\\s+"))
                fun vector(): FloatArray { require(fields.size == 4) { "LUT 范围需要三个数值" }; return FloatArray(3) { fields[it + 1].toFloat().also { v -> require(v.isFinite() && v in -64f..64f) } } }
                when (fields[0]) {
                    "TITLE" -> title = line.substringAfter(' ').trim().trim('"').take(80)
                    "LUT_3D_SIZE" -> { require(size == 0 && fields.size == 2); size = fields[1].toInt(); require(size in 2..65) { "支持 2–65 阶 3D LUT" }; samples = FloatArray(size * size * size * 3) }
                    "DOMAIN_MIN" -> minimum = vector()
                    "DOMAIN_MAX" -> maximum = vector()
                    "LUT_1D_SIZE" -> error("请选择独立的 3D .cube LUT，暂不支持混合 1D LUT")
                    else -> {
                        val target = requireNotNull(samples) { "缺少 LUT_3D_SIZE" }
                        require(fields.size == 3 && count + 3 <= target.size) { "LUT 数据行数不正确" }
                        fields.forEach { value -> target[count++] = value.toFloat().also { require(it.isFinite() && it in -64f..64f) { "LUT 数据无效" } }.coerceIn(0f, 1f) }
                    }
                }
            }
            val values = requireNotNull(samples) { "缺少 3D LUT 数据" }
            require(count == values.size && (0..2).all { maximum[it] > minimum[it] }) { "LUT 数据不完整或范围无效" }
            return CubeLut(title, size, minimum, maximum, values)
        }
    }
}
