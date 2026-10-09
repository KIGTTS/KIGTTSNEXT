@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.kigtts.next.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.AppHeader
import com.kigtts.next.ui.navigation.predictiveReturn
import com.kigtts.next.theme.LocalUseCompactControlPalette

@Composable
fun HomeScreen(
    state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel,
    openDrawer: () -> Unit,
    backProgress: Float = 0f, backEdge: Int = 0,
) {
    val sections = rememberSaveableStateHolder()
    var expandedViewport by remember { mutableFloatStateOf(852f) }
    CompositionLocalProvider(LocalUseCompactControlPalette provides true) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))
            .navigationBarsPadding()) {
            val topMaximum = (maxHeight.value * .4f).coerceAtLeast(112f)
            LaunchedEffect(maxHeight, state.subtitleExpanded) { if (!state.subtitleExpanded) expandedViewport = maxHeight.value }
            val motion = rememberHomeSheet(state, model, expandedViewport)
            val viewport = maxHeight.value
            val captionSpace = remember(motion, viewport) { {
                viewport - 64f - (if (motion.panel == HomePanel.QUICK_TEXT) motion.height else HomePanelSizes.COLLAPSED) -
                    12f - 180f - LISTENING_HANDLE_GAP
            } }
            val captionLayout by remember(captionSpace) { derivedStateOf { listeningLayout(captionSpace()) } }
            val showCaptions = !state.subtitleExpanded && !state.quickTextEditing && state.captionsVisible && captionLayout != ListeningLayout.HIDDEN
            val chrome = animateFloatAsState(if (state.subtitleExpanded || state.quickTextEditing) 0f else 1f,
                MaterialTheme.motionScheme.fastSpatialSpec(), label = "input-chrome-space")
            val enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                expandVertically(MaterialTheme.motionScheme.fastSpatialSpec(), expandFrom = Alignment.Top)
            val exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Top)
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(!state.subtitleExpanded && !state.quickTextEditing, enter = enter, exit = exit,
                    modifier = Modifier.testTag("header-transition")) {
                    sections.SaveableStateProvider("header") { AppHeader(state, model, openDrawer) }
                }
                Column(Modifier.weight(1f).horizontalPaddingFrom { 12f * chrome.value.coerceIn(0f, 1f) }) {
                    AnimatedVisibility(showCaptions, enter = enter, exit = exit,
                        modifier = Modifier.testTag("listening-transition")) {
                        sections.SaveableStateProvider("listening") {
                            ListeningPanel(state, model, topMaximum, captionLayout == ListeningLayout.SINGLE_LINE, captionSpace)
                        }
                    }
                    AnimatedVisibility(!state.quickTextEditing, enter = enter, exit = exit, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        TextEditor(state, model, showCaptions, !state.subtitleExpanded && motion.panel == HomePanel.QUICK_TEXT,
                            Modifier.fillMaxSize().testTag("editor-area")
                                .then(if (state.forcedFullscreen && state.sidePage == null) Modifier.predictiveReturn(backProgress, backEdge) else Modifier))
                    }
                    Spacer(Modifier.heightFrom {
                        val reserve = if (motion.panel == HomePanel.QUICK_TEXT) motion.height else HomePanelSizes.COLLAPSED
                        (reserve + 12f) * chrome.value.coerceIn(0f, 1f)
                    })
                }
            }
            AnimatedVisibility(!state.subtitleExpanded && !state.quickTextEditing, modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it }) {
                sections.SaveableStateProvider("quick-text") { HomePanels(state, phrases, categories, model, motion,
                Modifier.graphicsLayer {
                    translationY = if (state.sidePage == null) motion.height.dp.toPx() * backProgress * .7f else 0f
                }) }
            }
            AnimatedVisibility(state.quickTextEditing, modifier = Modifier.fillMaxSize(),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 6 },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 6 }) {
                QuickTextEditor(state, phrases, categories, model, Modifier.predictiveReturn(backProgress, backEdge))
            }
        }
    }
}
