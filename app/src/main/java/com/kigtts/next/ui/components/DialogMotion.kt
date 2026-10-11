package com.kigtts.next.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Bounded container motion: expansion settles gently; dismissal clears the content first. */
internal object DialogMotion {
    val enterEasing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val exitEasing = CubicBezierEasing(.3f, 0f, 1f, 1f)
    fun <T> compact(expanding: Boolean): FiniteAnimationSpec<T> = tween(
        if (expanding) 260 else 180, easing = if (expanding) enterEasing else exitEasing)
}

internal val LocalDialogReveal = compositionLocalOf { 1f }

/** Large visual surfaces may morph independently; text and small controls keep their native size. */
@Composable
internal fun Modifier.dialogElementMorph(): Modifier {
    val reveal = LocalDialogReveal.current
    return graphicsLayer { scaleY = .8f + .2f * reveal }
}
