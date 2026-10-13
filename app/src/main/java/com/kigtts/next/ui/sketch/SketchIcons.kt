package com.kigtts.next.ui.sketch

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** An eraser silhouette, rather than the magic-wand glyph used by the old toolbar. */
internal val SketchEraserIcon: ImageVector by lazy {
    ImageVector.Builder("SketchEraser", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(16.2f, 3f); lineTo(22f, 8.8f); lineTo(10.8f, 20f); lineTo(5f, 20f); lineTo(1f, 16f)
            lineTo(14f, 3f); close()
            moveTo(3.8f, 16f); lineTo(7.9f, 11.9f); lineTo(12.1f, 16.1f); lineTo(10.2f, 18f); lineTo(5.8f, 18f); close()
        }
        path(fill = SolidColor(Color.Black)) { moveTo(13f, 20f); lineTo(23f, 20f); lineTo(23f, 22f); lineTo(13f, 22f); close() }
    }.build()
}
