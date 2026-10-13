@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.kigtts.next.ui.sketch

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize

@Composable internal fun SketchCanvas(state: SketchState, enabled: Boolean, initialize: Boolean,
    background: Long, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val input = remember(state) { SketchInput(state) }
    val focus = remember { FocusRequester() }
    DisposableEffect(input, enabled) { onDispose { input.release() } }
    LaunchedEffect(initialize, canvasSize) { if (initialize) state.initializeCanvas(canvasSize, background) }
    Canvas(modifier.fillMaxSize().testTag("sketch-canvas").onSizeChanged { canvasSize = it }.clipToBounds()
        .focusRequester(focus).onFocusChanged { if (!it.hasFocus) input.keyboardEraser(false) }
        .onKeyEvent { event ->
            if (enabled && event.key == Key.E) { input.keyboardEraser(event.type == KeyEventType.KeyDown); true } else false
        }.focusable().pointerInteropFilter { event ->
            if (!enabled || !initialize) false else {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) focus.requestFocus()
                input.process(event, canvasSize, density)
            }
        }) {
        if (state.documentSize.width > 0) {
            val transform = state.viewport(canvasSize)
            withTransform({ translate(transform.origin.x, transform.origin.y); scale(transform.scale, transform.scale, pivot = androidx.compose.ui.geometry.Offset.Zero) }) {
                val width = state.documentSize.width.toFloat(); val height = state.documentSize.height.toFloat()
                clipRect(0f, 0f, width, height) {
                    drawRect(Color(state.background), size = Size(width, height))
                    drawContext.canvas.saveLayer(Rect(0f, 0f, width, height), Paint())
                    try { state.strokes.forEach { stroke ->
                        drawPath(strokePath(stroke, width, height).asComposePath(),
                            color = if (stroke.erase) Color.Black else Color(stroke.color), alpha = stroke.alpha,
                            blendMode = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver)
                    } } finally { drawContext.canvas.restore() }
                }
            }
        }
    }
}
