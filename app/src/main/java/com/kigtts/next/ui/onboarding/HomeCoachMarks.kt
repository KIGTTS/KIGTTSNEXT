package com.kigtts.next.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.state.AppState
import com.kigtts.next.state.Destination
import com.kigtts.next.state.HomePanel
import com.kigtts.next.state.QuickSortPreference
import com.kigtts.next.theme.ThemeText
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.home.HomePreviewGeometry
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun HomeCoachMarks(state: AppState, sorting: QuickSortPreference, geometry: HomePreviewGeometry,
    progress: FirstRunStore, foreground: Boolean) {
    val home = state.destination == Destination.Home && state.sidePage == null
    val openPanel = state.panel == HomePanel.QUICK_TEXT && !state.subtitleExpanded && !state.quickTextEditing
    val requestedSort by sorting.prompt.collectAsStateWithLifecycle()
    var visitedEditor by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.quickTextEditing, home, foreground, openPanel) {
        if (state.quickTextEditing && home && foreground) visitedEditor = true
        if (visitedEditor && !state.quickTextEditing && home && foreground) {
            visitedEditor = false
            if (openPanel && !progress.seen(HomeTip.QUICK_EDIT)) sorting.offer()
        }
    }

    val candidate = when {
        !foreground || !home || state.voiceInputActive -> null
        state.editing && !state.forcedFullscreen && state.mediaId == null && state.editorText.isNotBlank() -> HomeTip.CURSOR
        openPanel && requestedSort -> HomeTip.QUICK_EDIT
        openPanel -> HomeTip.PANEL
        else -> null
    }
    val anchor = candidate?.let { geometry.regions[it.anchor] }
    val anchored = anchor != null && anchor.width > 0f && anchor.height > 0f
    var visible by remember { mutableStateOf<HomeTip?>(null) }
    LaunchedEffect(candidate, anchored) {
        if (candidate == visible && anchored) return@LaunchedEffect
        val previous = visible
        visible = null
        if (previous == HomeTip.QUICK_EDIT && requestedSort) sorting.dismiss()
        if (candidate == null || !anchored) return@LaunchedEffect
        if (progress.seen(candidate)) {
            if (candidate == HomeTip.QUICK_EDIT) sorting.dismiss()
        } else {
            delay(280)
            visible = candidate
            progress.markSeen(candidate)
        }
    }

    if (visible != null && visible == candidate && anchor != null) {
        val tip = checkNotNull(visible)
        HomeTipBubble(tip, anchor, dismiss = {
            visible = null
            if (tip == HomeTip.QUICK_EDIT) sorting.dismiss()
        }, enable = {
            visible = null
            sorting.choose(true)
        })
    }
}

@Composable
private fun HomeTipBubble(tip: HomeTip, anchor: Rect, dismiss: () -> Unit, enable: () -> Unit) {
    val density = LocalDensity.current
    val presentedAnchor by rememberUpdatedState(anchor)
    val offset = with(density) { 8.dp.roundToPx() }
    val position = remember(offset) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection,
                popupContentSize: IntSize): IntOffset {
                val bounds = presentedAnchor
                val left = (bounds.center.x - popupContentSize.width / 2f).roundToInt()
                val above = bounds.top.roundToInt() - popupContentSize.height - offset
                val top = if (above >= offset) above else bounds.bottom.roundToInt() + offset
                return IntOffset(left.coerceIn(offset, (windowSize.width - popupContentSize.width - offset).coerceAtLeast(offset)),
                    top.coerceIn(offset, (windowSize.height - popupContentSize.height - offset).coerceAtLeast(offset)))
            }
        }
    }
    val show = remember(tip) { Animatable(0f) }
    LaunchedEffect(tip) { show.animateTo(1f, tween(170)) }
    Popup(position, properties = PopupProperties(focusable = false, dismissOnClickOutside = false, dismissOnBackPress = false)) {
        Box(Modifier.widthIn(max = 310.dp)) {
            Surface(Modifier.fillMaxWidth().graphicsLayer { alpha = show.value; translationY = 5.dp.toPx() * (1f - show.value) }
                .testTag("home-tip-${tip.name.lowercase(java.util.Locale.ROOT)}"),
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 4.dp) {
                val (title, detail) = when (tip) {
                    HomeTip.PANEL -> "onboarding.tip_panel" to "onboarding.tip_panel_body"
                    HomeTip.CURSOR -> "onboarding.tip_cursor" to "onboarding.tip_cursor_body"
                    HomeTip.QUICK_EDIT -> "onboarding.tip_edit" to "onboarding.tip_edit_body"
                }
                Column(Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ThemeText(uiText(title), style = MaterialTheme.typography.titleSmall)
                    ThemeText(uiText(detail), Modifier.padding(end = 6.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(feedbackClick(dismiss)) { Text(uiText(if (tip == HomeTip.QUICK_EDIT) "quick.not_now" else "onboarding.got_it")) }
                        if (tip == HomeTip.QUICK_EDIT) TextButton(feedbackClick(enable)) { Text(uiText("action.enable")) }
                    }
                }
            }
        }
    }
}
