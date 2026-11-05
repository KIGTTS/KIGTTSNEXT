package com.kigtts.next.identity

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** A seeded terrain field with real elevation contours, including saddles and separate peaks. */
internal object IdentityTerrainPattern {
    private const val columns = 126
    private const val rows = 72
    private const val pitch = 8f
    private data class Hill(val x: Float, val y: Float, val width: Float, val height: Float, val elevation: Float)

    private val field by lazy {
        val random = Random(2605)
        val hills = listOf(
            Hill(796f, 88f, 185f, 116f, 1.15f), Hill(1058f, 370f, 172f, 210f, .92f),
            Hill(612f, 430f, 140f, 194f, .75f), Hill(870f, 288f, 108f, 112f, -.52f),
        ) + List(6) {
            Hill(350f + random.nextFloat() * 710f, random.nextFloat() * 563f,
                54f + random.nextFloat() * 100f, 58f + random.nextFloat() * 105f,
                .3f * (random.nextFloat() * 2f - 1f))
        }
        Array(rows + 1) { row -> FloatArray(columns + 1) { column ->
            val u = column * pitch; val v = row * pitch
            val x = u + 23f * sin(v / 49f + u / 135f) + 10f * sin(v / 26f + u / 77f)
            val y = v + 28f * sin(u / 73f - v / 160f) + 9f * sin(u / 27f + v / 41f)
            var z = .07f * sin(x / 49f + y / 68f) + .036f * sin(x / 23f - y / 35f)
            hills.forEach { hill ->
                val dx = (x - hill.x) / hill.width; val dy = (y - hill.y) / hill.height
                z += hill.elevation * exp(-dx * dx - dy * dy)
            }
            z
        } }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        for (index in -3..19) {
            val path = contours(index * .063f + .012f)
            paint.strokeWidth = if (index % 5 == 0) 2.5f else 1.45f
            canvas.drawPath(path, paint)
        }
    }

    private fun contours(level: Float): Path {
        val points = HashMap<Int, PointF>()
        val links = HashMap<Int, MutableList<Int>>()
        fun link(first: Int, second: Int) {
            links.getOrPut(first) { ArrayList(2) }.add(second)
            links.getOrPut(second) { ArrayList(2) }.add(first)
        }
        fun edge(x: Int, y: Int, vertical: Boolean, start: Float, end: Float): Int {
            val id = (y * (columns + 1) + x) * 2 + if (vertical) 1 else 0
            points.getOrPut(id) {
                val f = ((level - start) / (end - start)).coerceIn(0f, 1f)
                if (vertical) PointF(x * pitch, (y + f) * pitch) else PointF((x + f) * pitch, y * pitch)
            }
            return id
        }
        val crossings = IntArray(4)
        for (y in 0 until rows) for (x in 0 until columns) {
            val a = field[y][x]; val b = field[y][x + 1]; val c = field[y + 1][x + 1]; val d = field[y + 1][x]
            val high = a >= level
            var count = 0
            if (high != (b >= level)) crossings[count++] = edge(x, y, false, a, b)
            if ((b >= level) != (c >= level)) crossings[count++] = edge(x + 1, y, true, b, c)
            if ((d >= level) != (c >= level)) crossings[count++] = edge(x, y + 1, false, d, c)
            if (high != (d >= level)) crossings[count++] = edge(x, y, true, a, d)
            if (count == 2) link(crossings[0], crossings[1])
            if (count == 4) {
                val next = if (((a + b + c + d) / 4 >= level) == high) 1 else 3
                link(crossings[0], crossings[next]); link(crossings[2], crossings[4 - next])
            }
        }
        return Path().apply {
            // Open lines first, then closed loops. Each intersected grid edge is one shared vertex.
            val starts = links.keys.sortedBy { links.getValue(it).size }
            val visited = HashSet<Int>()
            for (start in starts) {
                if (!visited.add(start)) continue
                val first = points.getValue(start)
                moveTo(first.x, first.y)
                var previous = -1
                var current = start
                while (true) {
                    val next = links.getValue(current).firstOrNull { it != previous } ?: break
                    val from = points.getValue(current); val to = points.getValue(next)
                    quadTo(from.x, from.y, (from.x + to.x) / 2f, (from.y + to.y) / 2f)
                    if (next == start) { lineTo(first.x, first.y); close(); current = start; break }
                    if (!visited.add(next)) break
                    previous = current; current = next
                }
                if (current != start) points.getValue(current).let { lineTo(it.x, it.y) }
            }
        }
    }
}
