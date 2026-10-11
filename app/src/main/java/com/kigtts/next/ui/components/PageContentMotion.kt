package com.kigtts.next.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Changes to declarative page elements fade without replacing their entire navigation layer. */
@Composable
fun <T> PageContentMotion(value: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(value, modifier, transitionSpec = {
        fadeIn(tween(180, delayMillis = 40)).togetherWith(fadeOut(tween(120)))
            .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(220) }))
    }, label = "page-content") { content(it) }
}
