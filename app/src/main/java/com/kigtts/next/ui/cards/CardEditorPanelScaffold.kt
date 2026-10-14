@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kigtts.next.ui.cards

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.homeCardShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One connected surface owns the handle, scrollable tools and native tabs. */
@Composable
internal fun CardEditorPanelScaffold(aspectRatio: Float, enabled: Boolean, expandTools: Boolean,
    clearSelection: () -> Unit, preview: @Composable (Modifier) -> Unit, tools: @Composable () -> Unit,
    categories: (@Composable (Boolean, () -> Unit) -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val toolsScroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = LocalCardWindowLandscape.current ?: (maxWidth > maxHeight)
        val floatingHeader = wide && LocalCardPageChrome.current != null
        BoxWithConstraints(Modifier.fillMaxSize().imePadding().clipToBounds().testTag("card-editor")) {
            val compactTabs = wide || maxHeight < 360.dp
            val header = if (wide || maxHeight < 240.dp) 44.dp else 56.dp
            val tabs = if (categories == null) 0.dp else if (compactTabs) 48.dp else 72.dp
            val axis = if (wide) maxWidth else maxHeight
            val collapsed = if (wide) header else minOf(header + tabs, axis)
            val expanded = if (wide) minOf(480.dp, axis * .58f)
                else minOf(520.dp, maxOf(axis * .72f, collapsed + 64.dp), axis)
            val partial = (if (wide) minOf(340.dp, axis * .42f) else axis * .50f)
                .coerceIn(collapsed, expanded)
            val density = LocalDensity.current
            val full = with(density) { axis.toPx() }
            val start = with(density) { (axis - expanded).toPx() }
            val middle = with(density) { (axis - partial).toPx() }
            val end = with(density) { (axis - collapsed).toPx() }
            val sheet = rememberCardToolsSheet(start, middle, end)
            RegisterCardPanelInset { if (wide) full - (sheet.offset.takeIf(Float::isFinite) ?: middle) else 0f }
            val drag = rememberCardPanelDrag(sheet, if (wide) Orientation.Horizontal else Orientation.Vertical, false, enabled)
            LaunchedEffect(expandTools) { if (expandTools) sheet.animateTo(CardToolsPosition.EXPANDED, tween(220)) }
            val openTools = { scope.launch { if (sheet.targetValue == CardToolsPosition.COLLAPSED)
                sheet.animateTo(CardToolsPosition.PARTIAL, tween(220)) }; Unit }
            val paperWidth = minOf((maxWidth - 40.dp).coerceAtLeast(1.dp), 340.dp)
            val stageHeight = maxHeight
            val photoTop = if (floatingHeader) 64.dp else 0.dp
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { clearSelection() } }
                .testTag("card-editor-preview"), contentAlignment = Alignment.Center) {
                preview(Modifier.width(paperWidth).graphicsLayer {
                    val offset = sheet.offset.takeIf(Float::isFinite)?.coerceIn(0f, full) ?: middle
                    val available = (offset - 24.dp.toPx()).coerceAtLeast(1f)
                    val paperHeight = size.width / aspectRatio.coerceAtLeast(.1f)
                    scaleX = if (wide) minOf(1f, available / size.width.coerceAtLeast(1f),
                        (stageHeight.toPx() - photoTop.toPx() - 24.dp.toPx()).coerceAtLeast(1f) / paperHeight)
                        else minOf(1f, available / paperHeight.coerceAtLeast(1f))
                    scaleY = scaleX
                    if (wide) { translationX = offset / 2f - full / 2f; translationY = photoTop.toPx() / 2f }
                    else translationY = offset / 2f - full / 2f
                })
            }
            val shape = homeCardShape().let { if (wide) it.copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
                else it.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)) }
            // Only the panel's viewport is measured while dragging; the photo stays in its layer.
            Surface(Modifier.layout { child, constraints ->
                val offset = (sheet.offset.takeIf(Float::isFinite) ?: middle).roundToInt()
                val extent = (full - offset).roundToInt().coerceAtLeast(0)
                val panelConstraints = if (wide) constraints.copy(minWidth = extent, maxWidth = extent)
                    else constraints.copy(minHeight = extent, maxHeight = extent)
                val measured = child.measure(panelConstraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    measured.place(if (wide) offset else 0, if (wide) 0 else offset)
                }
            }.testTag("card-editor-panel").semantics { stateDescription = sheet.targetValue.name },
                shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
                val handle: @Composable (Modifier) -> Unit = { modifier ->
                    CardEditorPanelHeader(wide, sheet.targetValue != CardToolsPosition.COLLAPSED, enabled, drag,
                        { scope.launch { sheet.animateTo(if (sheet.targetValue == CardToolsPosition.COLLAPSED)
                            CardToolsPosition.PARTIAL else CardToolsPosition.COLLAPSED, tween(220)) } }, modifier)
                }
                val content: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier.clipToBounds()) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(toolsScroll)
                            .testTag("card-editor-tools-scroll")) { tools() }
                        categories?.invoke(compactTabs, openTools)
                    }
                }
                if (wide) Row(Modifier.fillMaxSize()) {
                    handle(Modifier.width(header).fillMaxHeight())
                    content(Modifier.weight(1f).fillMaxHeight())
                } else Column(Modifier.fillMaxSize()) {
                    handle(Modifier.fillMaxWidth().height(header))
                    content(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}
