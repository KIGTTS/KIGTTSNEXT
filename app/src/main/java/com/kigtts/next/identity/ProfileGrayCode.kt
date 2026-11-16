package com.kigtts.next.identity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.zxing.common.reedsolomon.GenericGF
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder
import java.nio.ByteBuffer
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt

/** Private four-level lattice. The two bottom rows calibrate the palette and locate the RS blocks. */
internal object ProfileGrayCode {
    const val WIDTH = 840
    const val CELL = 4
    const val MARGIN = 32
    private const val LEGACY_LOGO_FOOTER = 76
    private const val LEFT = 36
    private const val COLUMNS = 192
    private const val ROW_BYTES = COLUMNS / 4
    private const val DATA_ROWS = 4
    private const val BLOCK_DATA = 152
    private const val BLOCK_TOTAL = DATA_ROWS * ROW_BYTES
    private const val DATA_LIMIT = 48 * 1024
    private const val PLAIN_LIMIT = 256 * 1024
    private val field = GenericGF.QR_CODE_FIELD_256
    private val magic = "KIGCARD1".toByteArray(Charsets.US_ASCII)
    private val ruler = intArrayOf(0, 3, 1, 3, 0, 2, 2, 1, 3, 0, 1, 2, 0, 3, 2, 1)

    internal class Code(private val rows: List<IntArray>) {
        val height = rows.size * CELL

        fun draw(canvas: Canvas, bottom: Int, accent: Int) {
            val paint = Paint()
            val colors = intArrayOf(18, 91, 163, 244).map { neutral ->
                fun mix(channel: Int) = (neutral * .78 + channel * .22).roundToInt()
                Color.rgb(mix(Color.red(accent)), mix(Color.green(accent)), mix(Color.blue(accent)))
            }
            rows.forEachIndexed { row, values ->
                val y = bottom - (row + 1) * CELL
                // Extend the pattern through the sides without shifting the original sampling grid.
                for (x in 0 until WIDTH step CELL) {
                    val column = (x - LEFT) / CELL
                    paint.color = colors[values[(column + COLUMNS) % COLUMNS]]
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + CELL).toFloat(), (y + CELL).toFloat(), paint)
                }
            }
        }
    }

    fun encode(json: String): Code {
        val plain = json.toByteArray(Charsets.UTF_8)
        require(plain.size <= PLAIN_LIMIT) { "名片资料过多，无法生成还原码" }
        val out = java.io.ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(plain) }
        val data = out.toByteArray()
        require(data.size in 1..DATA_LIMIT) { "名片资料过多，无法生成还原码" }
        val encoder = ReedSolomonEncoder(field)
        val crc = checksum(data)
        val header = ByteBuffer.allocate(32).put(magic).putInt(data.size).putInt(crc).array()
        fun coded(source: ByteArray, start: Int, count: Int, total: Int) =
            IntArray(total) { i -> if (i < count && start + i < source.size) source[start + i].toInt() and 255 else 0 }
                .also { encoder.encode(it, total - count) }
        return Code(buildList {
            add(IntArray(COLUMNS) { ruler[it % ruler.size] })
            add(coded(header, 0, header.size, ROW_BYTES).cells(0))
            for (start in data.indices step BLOCK_DATA) {
                val block = coded(data, start, BLOCK_DATA, BLOCK_TOTAL)
                repeat(DATA_ROWS) { add(block.cells(it * ROW_BYTES)) }
            }
        })
    }

    private fun IntArray.cells(start: Int) = IntArray(COLUMNS) { column ->
        this[start + column / 4] shr (6 - (column % 4) * 2) and 3
    }

    private fun checksum(bytes: ByteArray) = CRC32().apply { update(bytes) }.value.toInt()
    private fun luminance(color: Int) = (Color.red(color) * 3 + Color.green(color) * 6 + Color.blue(color)) / 10

    /** The reader works on a bottom band, rather than allocating the full long image. */
    fun decode(width: Int, height: Int, band: (Int) -> Bitmap?): String? {
        if (width !in 420..4096 || height < width / 2 || height > 40000) return null
        return listOf(0, LEGACY_LOGO_FOOTER, MARGIN).firstNotNullOfOrNull { margin -> decode(width, height, margin, band) }
    }

    private fun decode(width: Int, height: Int, margin: Int, band: (Int) -> Bitmap?): String? {
        val scale = width.toDouble() / WIDTH
        val footer = (margin * scale).roundToInt()
        val rowHeight = CELL * scale
        val headerBand = (footer + rowHeight * 3 + 4).roundToInt().coerceAtMost(height)
        val decoder = ReedSolomonDecoder(field)
        fun sample(image: Bitmap, offset: Int, row: Int): IntArray? {
            val y = image.height - footer - ((row + .5) * rowHeight).roundToInt() + offset
            if (y !in 0 until image.height) return null
            return IntArray(COLUMNS) { x ->
                val column = ((LEFT + (x + .5) * CELL) * scale).toInt().coerceIn(0, width - 1)
                luminance(image.getPixel(column, y))
            }
        }
        fun centers(values: IntArray): List<Int>? {
            val levels = (0..3).map { level -> values.filterIndexed { i, _ -> ruler[i % ruler.size] == level }.average().roundToInt() }
            if (levels.zipWithNext().any { (a, b) -> b - a < 29 }) return null
            if (values.indices.count { i -> kotlin.math.abs(values[i] - levels[ruler[i % ruler.size]]) > 21 } > 24) return null
            return levels
        }
        fun row(image: Bitmap, offset: Int, index: Int, levels: List<Int>): IntArray? {
            val samples = sample(image, offset, index) ?: return null
            return IntArray(ROW_BYTES) { i ->
                var value = 0
                repeat(4) { j ->
                    val level = levels.indices.minBy { kotlin.math.abs(levels[it] - samples[i * 4 + j]) }
                    value = (value shl 2) or level
                }
                value
            }
        }
        data class Header(val offset: Int, val levels: List<Int>, val size: Int, val crc: Int)
        val head = band(headerBand) ?: return null
        val header = try {
            listOf(0, -1, 1, -2, 2).firstNotNullOfOrNull { shift -> runCatching {
                val levels = centers(sample(head, shift, 0) ?: return@runCatching null) ?: return@runCatching null
                val bytes = row(head, shift, 1, levels) ?: return@runCatching null
                decoder.decode(bytes, ROW_BYTES - 32)
                val parsed = ByteBuffer.wrap(ByteArray(32) { bytes[it].toByte() })
                val found = ByteArray(magic.size).also(parsed::get)
                if (!magic.contentEquals(found)) return@runCatching null
                val size = parsed.int
                if (size !in 1..DATA_LIMIT) null else Header(shift, levels, size, parsed.int)
            }.getOrNull() }
        } finally { head.recycle() } ?: return null
        val count = (header.size + BLOCK_DATA - 1) / BLOCK_DATA
        val bandHeight = (footer + rowHeight * (count * DATA_ROWS + 2) + 4).roundToInt().coerceAtMost(height)
        val strip = band(bandHeight) ?: return null
        return try {
            val packed = ByteArray(header.size)
            for (block in 0 until count) {
                val bytes = IntArray(BLOCK_TOTAL)
                repeat(DATA_ROWS) { sub ->
                    val cells = row(strip, header.offset, 2 + block * DATA_ROWS + sub, header.levels) ?: return null
                    cells.copyInto(bytes, sub * ROW_BYTES)
                }
                decoder.decode(bytes, BLOCK_TOTAL - BLOCK_DATA)
                val start = block * BLOCK_DATA
                for (i in 0 until minOf(BLOCK_DATA, packed.size - start)) packed[start + i] = bytes[i].toByte()
            }
            if (checksum(packed) != header.crc) return null
            InflaterInputStream(packed.inputStream()).use {
                val plain = java.io.ByteArrayOutputStream()
                com.kigtts.next.legacy.LegacyArchive.copy(it, plain, PLAIN_LIMIT.toLong())
                plain.toString("UTF-8").takeIf(String::isNotBlank)
            }
        } catch (_: Exception) { null }
        finally { strip.recycle() }
    }
}
