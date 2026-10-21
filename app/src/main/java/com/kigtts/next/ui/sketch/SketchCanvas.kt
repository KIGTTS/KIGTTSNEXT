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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke as CursorStroke
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
    val scene = remember(state) { SketchScene() }
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
                val visible = Rect(transform.toDocument(androidx.compose.ui.geometry.Offset.Zero),
                    transform.toDocument(androidx.compose.ui.geometry.Offset(size.width, size.height)))
                drawSketchPaper(drawContext.canvas.nativeCanvas, visible, state.background.toInt(), state.pattern,
                    minOf(width, height), transform.scale)
                drawContext.canvas.saveLayer(visible, Paint())
                try {
                    scene.sync(state.strokes.toList(), width, height)
                    scene.draw(visible, transform.scale) { stroke, path ->
                    drawPath(path.asComposePath(),
                        color = if (stroke.erase) Color.Black else Color(stroke.color), alpha = stroke.alpha,
                        blendMode = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver)
                } } finally { drawContext.canvas.restore() }
            }
            state.eraserCursor?.let { cursor ->
                val radius = state.eraserSize * density * transform.scale / 2f
                drawCircle(Color.White.copy(alpha = .9f), radius, cursor, style = CursorStroke(3f * density))
                drawCircle(Color.Black.copy(alpha = .7f), radius, cursor, style = CursorStroke(density))
            }
        }
    }
}
