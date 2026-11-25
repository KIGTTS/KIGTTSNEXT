@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.theme.LocalUseCompactControlPalette
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.predictiveReturn
import com.kigtts.next.ui.navigation.ReturnProgress

@Composable
internal fun LandscapeHomeScreen(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel,
    openDrawer: () -> Unit, sections: SaveableStateHolder, back: State<ReturnProgress>, fade: HomeChromeFade) {
    var captionWidth by rememberSaveable { mutableFloatStateOf(160f) }
    CompositionLocalProvider(LocalUseCompactControlPalette provides true) {
        BoxWithConstraints(Modifier.fillMaxSize()
            .windowInsetsPadding(homeDrawingInsets().only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .smallWindowSafeArea().testTag("landscape-home")) {
            val expanded = state.subtitleExpanded || state.quickTextEditing
            val bodyWidth = maxWidth.value - LandscapePanelGeometry.RAIL - 24f
            // Reserve a minimum listening column first; the caption remains usable at every stop.
            val canListen = state.captionsVisible && bodyWidth >= LandscapePanelGeometry.MAIN_MINIMUM + 184f + 112f + LISTENING_HANDLE_GAP + 8f
            val listeningReserve = if (canListen) 112f + LISTENING_HANDLE_GAP else 0f
            val motion = rememberLandscapePanel(state, model, bodyWidth - LandscapePanelGeometry.MAIN_MINIMUM - listeningReserve - 8f)
            val captionMaximum = (bodyWidth - LandscapePanelGeometry.MAIN_MINIMUM - motion.geometry.maximum - LISTENING_HANDLE_GAP - 8f)
                .coerceIn(112f, 240f)
            val showCaptions = canListen && !expanded
            val chrome = animateFloatAsState(if (expanded) 0f else 1f, androidx.compose.animation.core.tween(220), label = "landscape-chrome")
            val enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + expandHorizontally(androidx.compose.animation.core.tween(220), Alignment.Start)
            val exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(androidx.compose.animation.core.tween(220), Alignment.Start)
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(!expanded, enter = enter, exit = exit, modifier = Modifier.testTag("header-transition").then(fade.header)) {
                    sections.SaveableStateProvider("header") { LandscapeAppRail(state, model, openDrawer) }
                }
                Row(Modifier.weight(1f).fillMaxHeight().horizontalPaddingFrom { 12f * chrome.value.coerceIn(0f, 1f) }
                    .verticalPaddingFrom { 4f * chrome.value.coerceIn(0f, 1f) }) {
                    AnimatedVisibility(showCaptions, enter = enter, exit = exit, modifier = Modifier.testTag("listening-transition")) {
                        sections.SaveableStateProvider("listening") {
                            ListeningPanel(state, model, captionMaximum, horizontal = true,
                                extent = captionWidth, resize = { captionWidth = it })
                        }
                    }
                    AnimatedVisibility(!state.quickTextEditing, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TextEditor(state, model, false, false,
                            Modifier.fillMaxSize().testTag("editor-area").then(if (state.forcedFullscreen && state.sidePage == null)
                                Modifier.predictiveReturn(back) else Modifier),
                            CornerNeighbors(start = showCaptions, end = !expanded && motion.visible))
                    }
                    AnimatedVisibility(!expanded, modifier = fade.dock,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + expandHorizontally(androidx.compose.animation.core.tween(220), Alignment.End),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkHorizontally(androidx.compose.animation.core.tween(220), Alignment.End)) {
                        sections.SaveableStateProvider("quick-text") {
                            Row(Modifier.fillMaxHeight()) {
                                Spacer(Modifier.width(8.dp))
                                LandscapeQuickPanel(state, phrases, categories, model, motion, Modifier.graphicsLayer {
                                    translationX = if (state.sidePage == null) motion.width.dp.toPx() * back.value.progress * .7f else 0f
                                })
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(state.quickTextEditing, modifier = Modifier.fillMaxSize(),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + slideInHorizontally(androidx.compose.animation.core.tween(220)) { it / 6 },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutHorizontally(androidx.compose.animation.core.tween(220)) { it / 6 }) {
                QuickTextEditor(state, phrases, categories, model, Modifier.predictiveReturn(back))
            }
        }
    }
}
