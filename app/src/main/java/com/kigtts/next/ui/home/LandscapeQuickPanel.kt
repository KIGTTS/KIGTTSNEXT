@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.ui.onboarding.HomeTip
import com.kigtts.next.ui.onboarding.homeTipAnchor
import com.kigtts.next.i18n.uiText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.plugins.SlotActions
import com.kigtts.plugin.api.*

@Composable
fun LandscapeQuickPanel(state: AppState, phrases: List<QuickPhrase>, categories: List<String>, model: AppViewModel,
    motion: LandscapePanelMotion, modifier: Modifier = Modifier) {
    val background by animateFloatAsState(if (state.panel != null) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "side-sheet-background")
    val showContent by remember(motion) { derivedStateOf { motion.visible && motion.width >= 136f } }
    val expandedDescription = uiText("quick.columns", "count" to motion.columns.toString())
    val collapsedDescription = uiText("quick.dock_closed")
    Box(modifier.fillMaxHeight().widthFrom { motion.width }.testTag("landscape-quick-panel").homePreviewRegion("phrases").semantics {
        stateDescription = if (motion.visible) expandedDescription else collapsedDescription
    }) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = background }
            .clip(homeCardShape(CornerNeighbors(start = true)))) {
            com.kigtts.next.theme.DrawerBackdrop(state.theme, Modifier.matchParentSize())
        }
        Row(Modifier.fillMaxSize()) {
            LandscapeSheetChrome(state, model, motion)
            AnimatedVisibility(showContent, Modifier.weight(1f).fillMaxHeight()) {
                QuickTextContent(state, phrases, categories, model, false, motion.columns,
                    Modifier.fillMaxSize().padding(vertical = 8.dp), categoryOpacity = {
                        ((motion.width - 136f) / (motion.geometry.widthFor(1) - 136f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    })
            }
        }
    }
}

@Composable
private fun LandscapeSheetChrome(state: AppState, model: AppViewModel, motion: LandscapePanelMotion) {
    val open = state.panel != null
    val openCamera = homeCameraShortcut(model)
    val progress = ((motion.width - LandscapePanelGeometry.COLLAPSED) / (motion.geometry.widthFor(1) - LandscapePanelGeometry.COLLAPSED)).coerceIn(0f, 1f)
    val context = model.context()
    val longPress = remember(context, state.pluginRevision) { model.registry.gestureActions(PluginGesture.QUICK_TEXT_LONG_PRESS, context).firstOrNull() }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val action = { if (model.state.value.panel == null) model.openPanel(HomePanel.QUICK_TEXT) else model.closePanel() }
    val click = feedbackClick(action)
    val hold = { if (state.continuousRecognition) model.endVoiceInput(false) else longPress?.let(model::startVoiceInputFromDock); Unit }
    val dockLabel = if (state.continuousRecognition) uiText("quick.dock_stop") else if (longPress == null) uiText("quick.dock") else uiText("quick.dock_voice")
    val sideSize=(48f-16f*progress).dp
    val shape = IconButtonDefaults.shapes(RoundedCornerShape(sideSize/2), RoundedCornerShape(8.dp))
    Column(Modifier.fillMaxHeight().widthFrom { LandscapePanelGeometry.CHROME + 24f * (1f - progress) }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        ToolButton(if (open) uiText("quick.edit") else uiText("quick.camera"), if (open) Icons.Default.Edit else Icons.Default.PhotoCamera,
            { if (open) model.editQuickTexts() else openCamera(false) },
            Modifier.size(sideSize).testTag("dock-start").homePreviewRegion("dock-start").homeTipAnchor(HomeTip.QUICK_EDIT, enabled = !motion.active), shapes = shape, size = sideSize,
            onLongClick = if (open) null else ({ openCamera(true) }),
            iconBlend = ToolIconBlend(Icons.Default.PhotoCamera, Icons.Default.Edit, progress))
        Box(Modifier.width(32.dp).heightFrom { 32f * progress }) {
            SlotActions(UiSlot.QUICK_PANEL_START, model.registry, model::invoke, Modifier.width(32.dp), context = context,
                revision = state.pluginRevision, visible = open && progress > .001f)
        }
        // This owner never changes when the pill morphs or plugin buttons are inserted.
        Box(Modifier.weight(1f).fillMaxWidth().then(motion.dragModifier).testTag("sheet-handle"), contentAlignment = Alignment.Center) {
            val thickness by animateFloatAsState(if (pressed && !open) 44f else 48f - 44f * progress + (if (motion.active) 3f * progress else 0f),
                MaterialTheme.motionScheme.fastSpatialSpec(), label = "vertical-dock-thickness")
            val length by animateFloatAsState(if (motion.active) 88f else 76f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "vertical-dock-length")
            val radius by animateFloatAsState(if (pressed && !open) 8f else 24f - 22f * progress,
                MaterialTheme.motionScheme.fastSpatialSpec(), label = "vertical-dock-radius")
            Box(Modifier.width(thickness.coerceAtLeast(2f).dp).heightFrom { 160f * (1f - progress) + length * progress }
                .testTag("dock-main").homePreviewRegion("dock-handle").homeTipAnchor(HomeTip.PANEL, enabled = !open && !motion.active)
                .clip(RoundedCornerShape(radius.coerceAtLeast(0f).dp)).background(MaterialTheme.colorScheme.primary)
                .clearAndSetSemantics {
                    contentDescription = dockLabel
                    role = Role.Button
                    onClick { click(); true }
                    if (longPress != null) onLongClick(longPress.action.label) { hold(); true }
                }.combinedClickable(interactionSource = source, indication = null, onClick = action,
                    onLongClick = if (longPress != null) hold else null, hapticFeedbackEnabled = longPress != null),
                contentAlignment = Alignment.Center) {
                Column(Modifier.graphicsLayer { alpha = 1f - progress }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.TextSnippet, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Text(if (state.continuousRecognition) uiText("quick.dock_vertical_stop") else uiText("quick.dock_vertical"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        Box(Modifier.width(32.dp).heightFrom { 32f * progress }) {
            SlotActions(UiSlot.QUICK_PANEL_END, model.registry, model::invoke, Modifier.width(32.dp), context = context,
                revision = state.pluginRevision, visible = open && progress > .001f)
        }
        ToolButton(if (open) uiText("quick.collapse") else uiText("quick.sketch"), if (open) Icons.Default.ChevronRight else Icons.Default.Draw,
            { if (open) model.closePanel() else model.openSidePage(SidePage.SKETCH) },
            Modifier.size(sideSize).testTag("dock-end").homePreviewRegion("dock-end"), shapes = shape, size = sideSize)
    }
}
