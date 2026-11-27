package com.kigtts.next.ui.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.QuickSortPreference
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import java.util.Locale

private class HomeTipTarget(val tip: HomeTip) {
    var bounds by mutableStateOf<Rect?>(null)
}

private val LocalHomeTipTarget = compositionLocalOf<HomeTipTarget?> { null }

@Composable
internal fun Modifier.homeTipAnchor(tip: HomeTip, enabled: Boolean = true): Modifier {
    val target = LocalHomeTipTarget.current?.takeIf { enabled && it.tip == tip } ?: return this
    DisposableEffect(target) { onDispose { target.bounds = null } }
    return onGloballyPositioned { target.bounds = it.boundsInRoot() }
}

@Composable
internal fun HomeCoachMarks(
    progress: FirstRunStore,
    sorting: QuickSortPreference,
    active: Boolean,
    subtitleEditing: Boolean,
    cursorAvailable: Boolean,
    quickDockCollapsed: Boolean,
    quickTextOpen: Boolean,
    quickTextEditing: Boolean,
    content: @Composable () -> Unit,
) {
    var seen by remember(progress) { mutableStateOf(HomeTip.entries.filter(progress::seen).toSet()) }
    var presented by remember { mutableStateOf<HomeTip?>(null) }
    var visitedEditor by rememberSaveable { mutableStateOf(false) }
    var visitedSubtitleInput by rememberSaveable { mutableStateOf(false) }
    val requestedSort by sorting.prompt.collectAsStateWithLifecycle()

    LaunchedEffect(active, subtitleEditing) {
        if (active && subtitleEditing && HomeTip.SUBTITLE_MENU !in seen) visitedSubtitleInput = true
    }

    LaunchedEffect(quickTextEditing, active, quickTextOpen) {
        if (!active) return@LaunchedEffect
        if (quickTextEditing) visitedEditor = true
        if (visitedEditor && !quickTextEditing) {
            visitedEditor = false
            if (quickTextOpen && HomeTip.QUICK_EDIT !in seen) sorting.offer()
        }
    }

    fun eligible(tip: HomeTip) = tip == presented || tip !in seen
    val candidate = when {
        !active -> null
        cursorAvailable -> HomeTip.CURSOR
        quickTextOpen && requestedSort -> HomeTip.QUICK_EDIT
        subtitleEditing || quickTextEditing -> null
        visitedSubtitleInput && eligible(HomeTip.SUBTITLE_MENU) -> HomeTip.SUBTITLE_MENU
        quickDockCollapsed -> HomeTip.PANEL
        else -> null
    }
    val pending = candidate?.takeIf(::eligible)
    val target = remember(pending) { pending?.let(::HomeTipTarget) }
    LaunchedEffect(candidate, requestedSort) {
        if (presented != candidate) presented = null
        if (requestedSort && HomeTip.QUICK_EDIT in seen && presented != HomeTip.QUICK_EDIT) sorting.dismiss()
    }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalHomeTipTarget provides target, content = content)
        if (target != null) SettledHomeTip(
            target,
            Modifier.matchParentSize().windowInsetsPadding(WindowInsets.safeDrawing),
            onPresented = {
                if (target.tip !in seen) {
                    presented = target.tip
                    seen = seen + target.tip
                    progress.markSeen(target.tip)
                }
            },
            dismiss = {
                presented = null
                if (target.tip == HomeTip.QUICK_EDIT) sorting.dismiss()
            },
            enable = {
                presented = null
                sorting.choose(true)
            },
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun SettledHomeTip(
    target: HomeTipTarget,
    modifier: Modifier,
    onPresented: () -> Unit,
    dismiss: () -> Unit,
    enable: () -> Unit,
) {
    var ready by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) {
        snapshotFlow { target.bounds }.debounce(280).first { it != null && !it.isEmpty }
        ready = true
    }
    if (!ready) return

    var origin by remember { mutableStateOf<Offset?>(null) }
    val preferSide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Layout(
        modifier = modifier.onGloballyPositioned { origin = it.positionInRoot() },
        content = { HomeTipBubble(target.tip, onPresented, dismiss, enable) },
    ) { measurables, constraints ->
        val margin = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val child = measurables.single().measure(Constraints(
            maxWidth = minOf(310.dp.roundToPx(), (width - margin * 2).coerceAtLeast(0)),
            maxHeight = (height - margin * 2).coerceAtLeast(0),
        ))
        layout(width, height) {
            val anchor = target.bounds ?: return@layout
            val viewportOrigin = origin ?: return@layout
            val position = homeTipOffset(IntSize(width, height), IntSize(child.width, child.height),
                anchor.translate(-viewportOrigin), margin, preferSide)
            position?.let { child.place(it.x, it.y) }
        }
    }
}

@Composable
private fun HomeTipBubble(tip: HomeTip, onPresented: () -> Unit, dismiss: () -> Unit, enable: () -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val (title, detail) = when (tip) {
        HomeTip.PANEL -> "onboarding.tip_panel" to if (landscape) "onboarding.tip_panel_landscape" else "onboarding.tip_panel_body"
        HomeTip.CURSOR -> "onboarding.tip_cursor" to "onboarding.tip_cursor_body"
        HomeTip.QUICK_EDIT -> "onboarding.tip_edit" to "onboarding.tip_edit_body"
        HomeTip.SUBTITLE_MENU -> "onboarding.tip_subtitle_menu" to "onboarding.tip_subtitle_menu_body"
    }
    key(tip) {
        CoachMarkBubble(uiText(title), uiText(detail), Modifier.testTag("home-tip-${tip.name.lowercase(Locale.ROOT)}"),
            onPresented, dismiss, enable.takeIf { tip == HomeTip.QUICK_EDIT })
    }
}
