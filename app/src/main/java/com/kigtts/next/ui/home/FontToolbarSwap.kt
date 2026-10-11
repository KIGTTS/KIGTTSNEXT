package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kigtts.next.ui.navigation.LocalPageActive

/** Both caption toolbars keep their height while buttons and the inline slider trade places. */
@Composable
internal fun FontToolbarSwap(shown: Boolean, label: String, modifier: Modifier, content: @Composable (Boolean) -> Unit) {
    val active = LocalPageActive.current
    AnimatedContent(shown, modifier.clipToBounds().testTag("font-swap-" + label), transitionSpec = {
        (fadeIn(tween(180, delayMillis = 30)) + slideInHorizontally(tween(220)) { if (targetState) -it / 12 else it / 12 })
            .togetherWith(fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { if (targetState) it / 12 else -it / 12 })
            .using(SizeTransform(clip = false))
    }, label = "inline-font-toolbar") { expanded ->
        CompositionLocalProvider(LocalPageActive provides (active && expanded == shown)) {
            Box(Modifier.fillMaxWidth().then(if (expanded != shown) Modifier.clearAndSetSemantics {} else Modifier)) { content(expanded) }
        }
    }
}
