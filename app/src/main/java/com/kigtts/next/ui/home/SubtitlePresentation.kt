package com.kigtts.next.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

internal data class SubtitleRenderFrame(val paragraph: AnnotatedString, val style: TextStyle, val placeholder: Boolean,
    val revision: Int = 0)
internal data class SubtitleLeavingFrame(val frame: SubtitleRenderFrame, val opacity: Float)
internal class SubtitleCrossfade(val leaving: List<SubtitleLeavingFrame>) {
    val alpha = Animatable(if (leaving.isEmpty()) 1f else 0f)
}

/** Capture what was visible before installing an incoming paragraph at zero opacity. */
internal class SubtitlePresentationMemory {
    var current: SubtitleRenderFrame? = null
    private var transition: SubtitleCrossfade? = null
    fun change(next: SubtitleRenderFrame, reducedMotion: Boolean = false): SubtitleCrossfade {
        val progress = transition?.alpha?.value ?: 1f
        val allowed = !reducedMotion || next.placeholder || next.revision != current?.revision
        val leaving = if (!allowed) emptyList() else buildList {
            transition?.leaving?.forEach { add(it.copy(opacity = it.opacity * (1f - progress))) }
            current?.let { add(SubtitleLeavingFrame(it, progress)) }
        }.filter { it.opacity > .005f }.takeLast(3)
        return SubtitleCrossfade(leaving).also { current = next; transition = it }
    }
}

@Composable
internal fun rememberSubtitlePresentation(frame: SubtitleRenderFrame, reducedMotion: Boolean): SubtitleCrossfade {
    val memory = remember { SubtitlePresentationMemory() }
    // Initial opacity belongs to this content key, not to a later SideEffect.
    val transition = remember(frame.paragraph, frame.placeholder) { memory.change(frame, reducedMotion) }
    SideEffect { memory.current = frame }
    LaunchedEffect(transition) {
        if (transition.alpha.value < 1f) transition.alpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
    }
    return transition
}
