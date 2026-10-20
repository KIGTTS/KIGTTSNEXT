package com.kigtts.next.ui.home

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import kotlin.math.sqrt

internal class VoiceSendArc(private val landscape: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = if (landscape) size.height else size.width
        val height = if (landscape) size.width else size.height
        val radius = (width * 1.35f - with(density) { 90.dp.toPx() }).coerceAtLeast(width / 2f + 1f)
        val path = Path()
        fun point(x: Float, y: Float, first: Boolean = false) {
            val px = if (landscape) y else x; val py = if (landscape) width - x else y
            if (first) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in 0..64) {
            val x = width * i / 64
            point(x, radius - sqrt((radius * radius - (x - width / 2f) * (x - width / 2f)).coerceAtLeast(0f)), i == 0)
        }
        point(width, height); point(0f, height); path.close()
        return Outline.Generic(path)
    }
}
