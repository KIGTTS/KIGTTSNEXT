@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.components.homeCardShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween

/** A native persistent tools sheet; its offset transforms the paper without remeasuring it every frame. */
@Composable
internal fun CardEditorPanelScaffold(aspectRatio: Float, enabled: Boolean, expandTools: Boolean,
    clearSelection: () -> Unit, preview: @Composable (Modifier) -> Unit, tools: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val toolsScroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().clipToBounds().testTag("card-editor")) {
        val panelHeight = minOf(maxHeight * .58f, 420.dp).coerceAtLeast(minOf(144.dp, maxHeight))
        val paperWidth = minOf((maxWidth - 40.dp).coerceAtLeast(1.dp), 340.dp)
        val density = LocalDensity.current
        val expandedTop = with(density) { (maxHeight - panelHeight).toPx() }
        val foldedTop = with(density) { (maxHeight - minOf(56.dp, maxHeight)).toPx() }
        val sheet = rememberCardToolsSheet(expandedTop, foldedTop)
        LaunchedEffect(expandTools) { if (expandTools) sheet.animateTo(CardToolsPosition.EXPANDED, tween(220)) }
        val stageHeight = maxHeight
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { clearSelection() })
        }.testTag("card-editor-preview"), contentAlignment = Alignment.Center) {
            preview(Modifier.width(paperWidth).graphicsLayer {
                val fullHeight = stageHeight.toPx()
                val top = sheet.offset.takeIf(Float::isFinite)?.coerceIn(0f, fullHeight) ?: expandedTop
                val available = (top - 24.dp.toPx()).coerceAtLeast(1f)
                val paperHeight = size.width / aspectRatio.coerceAtLeast(.1f)
                scaleX = minOf(1f, available / paperHeight.coerceAtLeast(1f)); scaleY = scaleX
                translationY = top / 2f - fullHeight / 2f
            })
        }
        // A whole-sheet nested scroll connection would consume the tool list's pre-fling and
        // edge drags. Bind Foundation's anchored drag solely to this header instead.
        Surface(Modifier.fillMaxWidth().height(panelHeight).offset {
            IntOffset(0, (sheet.offset.takeIf(Float::isFinite) ?: expandedTop).roundToInt())
        }.testTag("card-editor-panel"), shape = homeCardShape(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(56.dp).cardToolsDrag(sheet, enabled)
                        .testTag("card-editor-panel-header"), contentAlignment = Alignment.Center) {
                        BottomSheetDefaults.DragHandle(Modifier.testTag("card-editor-panel-handle"))
                        val open = sheet.targetValue == CardToolsPosition.EXPANDED
                        ToolButton(if (open) "收起编辑面板" else "展开编辑面板",
                            if (open) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            { scope.launch { sheet.animateTo(if (open) CardToolsPosition.COLLAPSED else CardToolsPosition.EXPANDED, tween(220)) } },
                            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).testTag("toggle-card-editor-panel"),
                            shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(8.dp)),
                            size = 32.dp, enabled = enabled)
                    }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(toolsScroll)
                        .testTag("card-editor-tools-scroll")) { tools() }
                }
        }
    }
}
