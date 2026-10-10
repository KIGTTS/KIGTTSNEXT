@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.kigtts.next.ui.sketch

import com.kigtts.next.ui.components.feedbackClick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun SketchScreen(state: SketchState, shareRequest: Int, shareConsumed: () -> Unit, message: (String) -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val input = remember(state) { SketchInput(state) }
    DisposableEffect(input) { onDispose { input.release() } }
    val focus = remember { FocusRequester() }
    var clear by remember { mutableStateOf(false) }
    fun export(share: Boolean) {
        runCatching { exportSketch(context, state.strokes.toList(), background.toArgb(), share, canvasSize) }
            .onSuccess { if (!share) message("画板已保存到应用内") }.onFailure { message("无法导出画板") }
    }
    LaunchedEffect(shareRequest) { if (shareRequest > 0) { export(true); shareConsumed() } }
    Surface(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), shape = MaterialTheme.shapes.large, color = background) {
        Box {
            Canvas(Modifier.fillMaxSize().padding(bottom = 80.dp).testTag("sketch-canvas")
                .onSizeChanged { canvasSize = it }.clipToBounds()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .focusRequester(focus).onFocusChanged { if (!it.hasFocus) input.keyboardEraser(false) }
                .onKeyEvent { event ->
                    if (event.key == Key.E) { input.keyboardEraser(event.type == KeyEventType.KeyDown); true } else false
                }.focusable().pointerInteropFilter { event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) focus.requestFocus()
                    input.process(event, canvasSize, density)
                }) {
                state.strokes.forEach { stroke ->
                    drawPath(strokePath(stroke, size.width, size.height).asComposePath(),
                        color = if (stroke.erase) Color.Black else Color(stroke.color),
                        alpha = stroke.alpha, blendMode = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver)
                }
            }
            if (state.strokes.none { !it.erase }) Icon(Icons.Default.Draw, null,
                Modifier.align(Alignment.Center).size(30.dp), tint = MaterialTheme.colorScheme.primary)
            SketchToolbar(state, { export(false) }, { clear = true },
                Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
    if (clear) MotionAlertDialog(onDismissRequest = { clear = false }, title = { Text("清空画板？") },
        text = { Text("当前笔画将被移除。已保存的图片不受影响。") },
        confirmButton = { TextButton(onClick = dialogFeedbackClick { state.clear(); clear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = dialogFeedbackClick { clear = false }) { Text("取消") } })
}
