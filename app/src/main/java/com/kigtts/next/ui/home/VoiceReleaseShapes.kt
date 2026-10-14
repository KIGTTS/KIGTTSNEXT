package com.kigtts.next.ui.home

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*

/** A bowed capsule rather than a rotated rectangular button. Its entire outline stays in bounds. */
internal object VoiceArcCapsule : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val bow = h * .18f; val radius = minOf((h - bow) / 2f, w / 2f)
        return Outline.Generic(Path().apply {
            moveTo(radius, bow)
            quadraticTo(w / 2f, -bow, w - radius, bow)
            cubicTo(w - radius * .45f, bow, w, bow + radius * .45f, w, bow + radius)
            cubicTo(w, h - radius * .45f, w - radius * .45f, h, w - radius, h)
            quadraticTo(w / 2f, h - bow * 2f, radius, h)
            cubicTo(radius * .45f, h, 0f, h - radius * .45f, 0f, bow + radius)
            cubicTo(0f, bow + radius * .45f, radius * .45f, bow, radius, bow)
            close()
        })
    }
}

internal class VoiceSendArc(private val landscape: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            if (landscape) {
                moveTo(size.width * .28f, 0f)
                quadraticTo(-size.width * .18f, size.height * .5f, size.width * .28f, size.height)
                lineTo(size.width, size.height); lineTo(size.width, 0f)
            } else {
                moveTo(0f, size.height * .28f)
                quadraticTo(size.width * .5f, -size.height * .18f, size.width, size.height * .28f)
                lineTo(size.width, size.height); lineTo(0f, size.height)
            }
            close()
        })
    }
}
