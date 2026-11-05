package com.kigtts.next.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection

/** Move between pages inside the same modal, leaving the window and scrim in place. */
@Composable
internal fun <T> ModalPageTransition(page: T, depth: (T) -> Int, modifier: Modifier = Modifier, pageKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit) {
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    AnimatedContent(page, modifier.clipToBounds(), contentKey = pageKey, transitionSpec = {
        val direction = if (depth(targetState) < depth(initialState)) -forward else forward
        (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { direction * it / 7 } +
            fadeIn(tween(180, delayMillis = 30))) togetherWith
            (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -direction * it / 7 } +
                fadeOut(tween(140)))
    }, label = "modal-page") { visible ->
        Box(if (pageKey(visible) == pageKey(page)) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
            // The departing page is visual only while it fades away.
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }) { content(visible) }
    }
}
