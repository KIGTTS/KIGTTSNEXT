package com.kigtts.next.ui.media

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.TransformOrigin
import com.kigtts.next.media.MediaScale

internal fun videoFrameScale(width:Int,height:Int,source:IntSize,scale:MediaScale):Float =
    if(scale==MediaScale.FIT)minOf(width.toFloat()/source.width.coerceAtLeast(1),height.toFloat()/source.height.coerceAtLeast(1))
        else maxOf(width.toFloat()/source.width.coerceAtLeast(1),height.toFloat()/source.height.coerceAtLeast(1))

/** Fixed native raster with a uniform RenderNode transform, including paused cached buffers. */
internal fun Modifier.videoFrameLayout(source:IntSize,scale:MediaScale)=layout { measurable,constraints ->
    val width=source.width.coerceAtLeast(1);val height=source.height.coerceAtLeast(1)
    val factor=videoFrameScale(constraints.maxWidth,constraints.maxHeight,source,scale)
    // Keep the actual TextureView/Surface raster constant. Changing it can release a queued
    // decoder frame even when playback is paused. Only the RenderNode transform follows layout.
    val frame=measurable.measure(Constraints.fixed(width,height))
    layout(constraints.maxWidth,constraints.maxHeight) {
        frame.placeWithLayer((constraints.maxWidth-width)/2,(constraints.maxHeight-height)/2) {
            scaleX=factor;scaleY=factor;transformOrigin=TransformOrigin.Center
        }
    }
}
