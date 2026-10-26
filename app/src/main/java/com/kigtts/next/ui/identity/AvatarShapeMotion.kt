package com.kigtts.next.ui.identity

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import com.kigtts.next.identity.AvatarFrames

/** Equal arc-length samples let an interrupted transition start at the exact visible contour. */
internal class AvatarShapeMotion(initial: String) {
    internal class Segment(val from: FloatArray, val to: FloatArray, val id: String, initial: Float) {
        val progress = Animatable(initial)
        fun points() = FloatArray(from.size) { i -> from[i] + (to[i] - from[i]) * progress.value }
    }
    var segment by mutableStateOf(Segment(AvatarFrames.points(initial), AvatarFrames.points(initial), initial, 1f))
        private set
    suspend fun select(id: String) {
        if (segment.id == id) return
        val next = Segment(segment.points(), AvatarFrames.points(id), id, 0f)
        segment = next
        next.progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    }
}

@Composable internal fun rememberAvatarShape(id: String): Shape {
    val motion = remember { AvatarShapeMotion(id) }
    LaunchedEffect(id) { motion.select(id) }
    val segment = motion.segment
    val progress = segment.progress.value
    return remember(segment, progress) {
        if (progress >= 1f) AvatarFrames.shape(segment.id) else object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val path = Path()
                for (i in segment.from.indices step 2) {
                    val x = (segment.from[i] + (segment.to[i] - segment.from[i]) * progress) * size.width
                    val y = (segment.from[i + 1] + (segment.to[i + 1] - segment.from[i + 1]) * progress) * size.height
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); return Outline.Generic(path)
            }
        }
    }
}
