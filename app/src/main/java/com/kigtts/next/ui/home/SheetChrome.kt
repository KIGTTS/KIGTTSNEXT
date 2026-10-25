@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.kigtts.next.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.plugins.SlotActions
import com.kigtts.plugin.api.UiSlot
import com.kigtts.plugin.api.PluginGesture

@Composable
fun SheetChrome(state: AppState, model: AppViewModel, motion: HomeSheetMotion, editPhrases: () -> Unit) {
    val open = state.panel != null
    val progress = ((motion.height - HomePanelSizes.COLLAPSED) /
        (HomePanelSizes.PEEK - HomePanelSizes.COLLAPSED)).coerceIn(0f, 1f)
    val sideSize = (48f - 16f * progress).dp
    val chromeHeight = (64f - 16f * progress).dp
    val sources = remember { List(5) { MutableInteractionSource() } }
    val shapes = IconButtonDefaults.shapes(RoundedCornerShape(sideSize / 2), RoundedCornerShape(8.dp))
    val context = model.context()
    val startSlot = open && remember(context, state.pluginRevision) { model.registry.actions(UiSlot.QUICK_PANEL_START, context).isNotEmpty() }
    val endSlot = open && remember(context, state.pluginRevision) { model.registry.actions(UiSlot.QUICK_PANEL_END, context).isNotEmpty() }
    val startAmount by animateFloatAsState(if (startSlot) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dock-start-slot")
    val endAmount by animateFloatAsState(if (endSlot) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dock-end-slot")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(chromeHeight)
        .snackbarAnchor(!state.subtitleExpanded && motion.height <= 80f).testTag("portrait-bottom-bar")) {
        val a = startAmount.coerceIn(0f, 1f); val b = endAmount.coerceIn(0f, 1f)
        val gaps = (2f + a + b) * 6f
        val slotWidth = ((maxWidth.value - 2 * sideSize.value - gaps - 88f) / (a + b).coerceAtLeast(1f)).coerceIn(0f, 64f)
        val centerWidth = (maxWidth.value - 2 * sideSize.value - (a + b) * slotWidth - gaps).coerceAtLeast(0f)
        val base = listOf(sideSize.value, slotWidth * a, centerWidth, slotWidth * b, sideSize.value)
        val widths = rememberBalancedWidths(base, sources)
        Row(Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically) {
            // Five stable identities: inserting extension slots must never dispose the live drag node.
            (0..4).forEach { index -> key(index) {
                val width = widths[index].dp
                when (index) {
                    0 -> ToolButton(if (open) "编辑快捷文本" else "画板", if (open) Icons.Default.Edit else Icons.Default.Draw,
                        { if (open) editPhrases() else model.openSidePage(SidePage.SKETCH) },
                        Modifier.width(width).testTag("dock-start").homePreviewRegion("dock-start"), sources[0], shapes, size = sideSize)
                    1 -> SlotActions(UiSlot.QUICK_PANEL_START, model.registry, model::invoke, Modifier.width(width).clipToBounds(), context = context, revision = state.pluginRevision, visible = startSlot)
                    2 -> DockHandle(state, model, motion, sources[2], progress, Modifier.width(width).height(chromeHeight))
                    3 -> SlotActions(UiSlot.QUICK_PANEL_END, model.registry, model::invoke, Modifier.width(width).clipToBounds(), context = context, revision = state.pluginRevision, visible = endSlot)
                    4 -> ToolButton(if (open) "收起快捷文本" else "拍立得", if (open) Icons.Default.ExpandMore else Icons.Default.PhotoLibrary,
                        { if (open) model.closePanel() else model.openSidePage(SidePage.CARDS) },
                        Modifier.width(width).testTag("dock-end").homePreviewRegion("dock-end"), sources[4], shapes, size = sideSize,
                        onLongClick = if (!open) ({ model.cardCapture.open() }) else null,
                        iconBlend = ToolIconBlend(Icons.Default.PhotoLibrary, Icons.Default.ExpandMore, progress))
                }
                if (index < 4) Spacer(Modifier.width((when (index) { 0 -> a; 3 -> b; else -> 1f } * 6f).dp))
            } }
        }
    }
}

@Composable
private fun DockHandle(state: AppState, model: AppViewModel, motion: HomeSheetMotion,
    source: MutableInteractionSource, progress: Float, modifier: Modifier) {
    val open = state.panel != null
    val context = model.context()
    val longPress = remember(context, state.pluginRevision) {
        model.registry.gestureActions(PluginGesture.QUICK_TEXT_LONG_PRESS, context).firstOrNull()
    }
    val action = { if (open) model.closePanel() else model.openPanel(HomePanel.QUICK_TEXT) }
    val click = feedbackClick(action)
    val hold = { if (state.continuousRecognition) model.endVoiceInput(false) else longPress?.let { model.voiceInputGesture.arm(); model.invoke(it.plugin.descriptor.id, it.action.id) }; Unit }
    val label = if (state.continuousRecognition) "长按停止识别" else if (longPress == null) "快捷文本" else "快捷文本 / 长按语音输入"
    val pressed by source.collectIsPressedAsState()
    val height by animateDpAsState(if (pressed && !open) 44.dp else (48f - 43f * progress + if (motion.active) 2f * progress else 0f).dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "dock-handle-height")
    val radius by animateDpAsState(if (pressed && !open) 8.dp else (24f - 21.5f * progress).dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "dock-handle-corner")
    BoxWithConstraints(modifier.then(motion.dragModifier).testTag("sheet-handle"), contentAlignment = Alignment.Center) {
        val gripWidth by animateDpAsState(if (motion.active) 88.dp else 76.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dock-grip-width")
        Box(Modifier.width(maxWidth * (1f - progress) + gripWidth * progress).height(height)
            .testTag("dock-main").homePreviewRegion("dock-handle").clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.primary)
            .clearAndSetSemantics {
                contentDescription = label; role = Role.Button
                onClick { click(); true }
                if (longPress != null) onLongClick(longPress.action.label) { hold(); true }
            }
            .combinedClickable(interactionSource = source, indication = null, role = Role.Button,
                onClick = if (open) action else click, hapticFeedbackEnabled = longPress != null,
                onLongClickLabel = longPress?.action?.label,
                onLongClick = hold),
                contentAlignment = Alignment.Center) {
            Row(Modifier.alpha(1f - progress), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TextSnippet, null, Modifier.size((24f - 8f * progress).dp),
                    tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp))
                Text(label, color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
