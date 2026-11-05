package com.kigtts.next.ui.album

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

internal fun List<LazyGridItemInfo>.nearestAlbumItem(point: Offset,
    allowed: (LazyGridItemInfo) -> Boolean = { true }): LazyGridItemInfo? =
    asSequence().filter(allowed).minByOrNull { item ->
        val dx = maxOf(item.offset.x - point.x, point.x - item.offset.x - item.size.width, 0f)
        val dy = maxOf(item.offset.y - point.y, point.y - item.offset.y - item.size.height, 0f)
        dx * dx + dy * dy
    }

internal class AlbumGridReflow(grid: LazyGridState) {
    data class Transition(val from: Map<Any, Rect>, val progress: Animatable<Float, *> = Animatable(0f))
    var transition by mutableStateOf<Transition?>(null)
    val current by derivedStateOf {
        grid.layoutInfo.visibleItemsInfo.associate { item ->
            val x = item.offset.x.toFloat(); val y = item.offset.y.toFloat()
            item.key to Rect(x, y, x + item.size.width, y + item.size.height)
        }
    }
    fun begin() {
        val old = transition
        val fraction = old?.progress?.value ?: 1f
        transition = Transition(current.mapValues { (key, to) ->
            old?.from?.get(key)?.let { from ->
                fun lerp(start: Float, end: Float) = start + (end - start) * fraction
                Rect(lerp(from.left, to.left), lerp(from.top, to.top), lerp(from.right, to.right), lerp(from.bottom, to.bottom))
            } ?: to
        })
    }
}

@Composable internal fun rememberAlbumGridReflow(grid: LazyGridState): AlbumGridReflow {
    val reflow = remember(grid) { AlbumGridReflow(grid) }
    val transition = reflow.transition
    LaunchedEffect(transition) {
        if (transition != null) {
            transition.progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            if (reflow.transition === transition) reflow.transition = null
        }
    }
    return reflow
}

internal fun Modifier.albumReflow(reflow: AlbumGridReflow, key: Any, resize: Boolean = true) = graphicsLayer {
    val transition = reflow.transition
    val remaining = 1f - (transition?.progress?.value ?: 1f)
    val from = transition?.from?.get(key)
    val to = if (transition != null) reflow.current[key] else null
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = if (from != null && to != null) (from.left - to.left) * remaining else 0f
    translationY = if (from != null && to != null) (from.top - to.top) * remaining else 0f
    scaleX = if (resize && from != null && to != null && to.width > 0f) 1f + (from.width / to.width - 1f) * remaining else 1f
    scaleY = if (resize && from != null && to != null && to.height > 0f) 1f + (from.height / to.height - 1f) * remaining else 1f
    alpha = if (transition != null && from == null) 1f - remaining else 1f
}
