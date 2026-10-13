package com.kigtts.next.ui.cards

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/** Album slides clip sideways; the rising dock retains its independent vertical drawing mask. */
internal fun Modifier.polaroidAlbumDrawingClip() = drawWithContent {
    val room = 208.dp.toPx()
    // A clipping RenderNode also constrains descendant coordinates to its measured height.
    // This drawing-only mask leaves the fixed overflow room to the independent dock motion.
    clipRect(left = 0f, top = -room, right = size.width, bottom = size.height + room) {
        this@drawWithContent.drawContent()
    }
}
