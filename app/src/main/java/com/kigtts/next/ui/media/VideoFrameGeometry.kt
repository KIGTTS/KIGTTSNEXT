package com.kigtts.next.ui.media

import com.kigtts.next.media.MediaScale
import kotlin.math.roundToInt

/** One immutable geometry per frame: framebuffer size and shader aspect must agree. */
internal data class VideoFrameGeometry(val width:Int,val height:Int,val scale:MediaScale) {
    val aspect:Float get()=width.toFloat()/height
}

internal fun videoFrameGeometry(side:Int,viewportAspect:Float,scale:MediaScale):VideoFrameGeometry {
    val edge=side.coerceAtLeast(1)
    val ratio=viewportAspect.takeIf { it.isFinite()&&it>0f } ?: 1f
    return if(ratio>=1f)VideoFrameGeometry(edge,(edge/ratio).roundToInt().coerceIn(1,edge),scale)
        else VideoFrameGeometry((edge*ratio).roundToInt().coerceIn(1,edge),edge,scale)
}
