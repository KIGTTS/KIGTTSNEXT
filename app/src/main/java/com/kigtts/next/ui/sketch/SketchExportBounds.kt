package com.kigtts.next.ui.sketch

import androidx.compose.ui.geometry.Rect

/** Include the full brush radius. Eraser movement alone must not enlarge a saved picture. */
internal fun sketchInkBounds(document: SketchDocument): Rect? {
    var left = Float.POSITIVE_INFINITY; var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY; var bottom = Float.NEGATIVE_INFINITY
    val shorter = minOf(document.width, document.height).toFloat()
    document.strokes.forEach { stroke -> if (!stroke.erase && stroke.alpha > 0f && ((stroke.color ushr 24) and 255L) != 0L) {
        stroke.points.forEach { point ->
            val x = point.position.x * document.width; val y = point.position.y * document.height
            val radius = point.width * shorter / 2f
            left = minOf(left, x - radius); right = maxOf(right, x + radius)
            top = minOf(top, y - radius); bottom = maxOf(bottom, y + radius)
        }
    } }
    return if (left.isFinite()) Rect(left, top, right, bottom) else null
}

/** Empty drawings retain a useful page. Ink gets an edge-relative margin plus a minimum brush-safe border. */
internal fun sketchExportBounds(document: SketchDocument): Rect {
    val ink = sketchInkBounds(document) ?: return Rect(0f, 0f, document.width.toFloat(), document.height.toFloat())
    val padding = maxOf(24f, minOf(document.width, document.height) * .06f, maxOf(ink.width, ink.height) * .04f)
    return Rect(ink.left - padding, ink.top - padding, ink.right + padding, ink.bottom + padding)
}
