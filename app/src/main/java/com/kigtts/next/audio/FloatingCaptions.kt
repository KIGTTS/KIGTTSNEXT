package com.kigtts.next.audio

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kigtts.next.overlay.OverlayViewOwner
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.i18n.UiLanguage
import com.kigtts.next.theme.KigttsTheme
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.next.ui.components.CornerNeighbors
import com.kigtts.next.ui.components.LocalCornerRadius
import com.kigtts.next.ui.components.connectedShape
import com.kigtts.next.ui.components.ProvideButtonFeedback
import com.kigtts.next.ui.components.ToolButton
import com.kigtts.next.ui.home.ListeningCaptionContent
import com.kigtts.next.ui.home.CaptionVisuals
import com.kigtts.next.ui.home.subtitleZoom
import kotlin.math.roundToInt

/** The overlay observes the same appearance as the app, but owns its own window lifecycle. */
internal class FloatingCaptions(context: Context, private val toggle: () -> Unit, private val dismiss: () -> Unit,
    private val resize: (Float) -> Unit, private val scaleSize: (Float) -> Unit, private val saveSize: () -> Unit) {
    private val context by lazy { if (Build.VERSION.SDK_INT >= 30) runCatching {
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        if (display == null) context else context.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }.getOrDefault(context) else context }
    private val windows by lazy { this.context.getSystemService(WindowManager::class.java) }
    private val density get() = context.resources.displayMetrics.density
    private var view: ComposeView? = null
    private var owner: OverlayViewOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var offset: Float? = null
    private var frameScheduled = false
    private var content by mutableStateOf(Content())

    fun show(text: String, size: Float, theme: ThemeSettings, active: Boolean, haptics: Boolean, locale: String, visuals: CaptionVisuals) {
        content = Content(text.takeLast(4000), size, theme, active, haptics, locale, visuals)
        if (view != null) return
        val width = availableWidth()
        val layout = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            title = "KIGTTS 字幕浮窗"
            y = (offset ?: (92 * density)).roundToInt().coerceIn(0, availableHeight())
        }
        val lifecycle = OverlayViewOwner()
        val window = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            lifecycle.attach(this)
            setContent {
                val state = content
                CompositionLocalProvider(LocalUiLocale provides state.locale) {
                    KigttsTheme(state.theme) {
                        ProvideButtonFeedback(state.haptics) {
                            FloatingCaptionContent(state, ::dragBy, toggle, dismiss, resize, scaleSize, saveSize)
                        }
                    }
                }
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> requestLayoutUpdate() }
        }
        try {
            windows.addView(window, layout)
            view = window; owner = lifecycle; params = layout
        } catch (error: RuntimeException) {
            window.disposeComposition(); lifecycle.destroy()
            throw error
        }
    }

    private fun availableWidth() = (context.resources.displayMetrics.widthPixels * .88f)
        .roundToInt().coerceIn(1, (560 * density).roundToInt())
    private fun availableHeight() = context.resources.displayMetrics.heightPixels.coerceAtLeast(1)

    private fun dragBy(delta: Float) {
        val window = view ?: return
        offset = ((offset ?: params?.y?.toFloat() ?: 0f) - delta)
            .coerceIn(0f, (availableHeight() - window.height - 16 * density).coerceAtLeast(0f))
        requestLayoutUpdate()
    }

    private fun requestLayoutUpdate() {
        val window = view ?: return
        if (frameScheduled) return
        frameScheduled = true
        window.postOnAnimation {
            if (view !== window) return@postOnAnimation
            frameScheduled = false
            val layout = params ?: return@postOnAnimation
            val width = availableWidth()
            val y = (offset ?: layout.y.toFloat()).roundToInt()
                .coerceIn(0, (availableHeight() - window.height - (16 * density).toInt()).coerceAtLeast(0))
            if (layout.width == width && layout.y == y) return@postOnAnimation
            layout.width = width; layout.y = y
            runCatching { windows.updateViewLayout(window, layout) }
        }
    }

    fun close() {
        val window = view ?: return
        view = null; frameScheduled = false
        runCatching { windows.removeViewImmediate(window) }
        window.disposeComposition(); owner?.destroy(); owner = null; params = null
    }

    internal data class Content(val text: String = "", val size: Float = 20f, val theme: ThemeSettings = ThemeSettings(),
        val active: Boolean = false, val haptics: Boolean = true, val locale: String = UiLanguage.STANDARD.tag,
        val visuals: CaptionVisuals = CaptionVisuals())
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingCaptionContent(state: FloatingCaptions.Content, move: (Float) -> Unit, toggle: () -> Unit, close: () -> Unit,
    resize: (Float) -> Unit, scaleSize: (Float) -> Unit, saveSize: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var showSize by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHigh, contentColor = colors.onSurface) {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CaptionTool("调整浮窗字号", "format_size", Icons.Default.FormatSize) { showSize = !showSize }
                Box(Modifier.weight(1f).height(44.dp)
                    .draggable(rememberDraggableState(move), Orientation.Vertical)
                    .semantics { contentDescription = "拖动字幕浮窗" }, contentAlignment = Alignment.CenterStart) {
                    Surface(Modifier.padding(start = 4.dp).size(32.dp, 4.dp),
                        shape = MaterialTheme.shapes.extraSmall, color = colors.outlineVariant) {}
                }
                CaptionWindowActions(state.active, toggle, close)
            }
            if (showSize) Slider(state.size, resize, Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                .semantics { contentDescription = "浮窗字号" }, valueRange = CaptionFontSettings.WINDOW_RANGE, onValueChangeFinished = saveSize)
            val maxHeight = with(LocalDensity.current) { (state.size * 5.4f).sp.toDp() }.coerceAtMost(240.dp)
            ListeningCaptionContent(state.text, state.size, singleLine = false,
                Modifier.fillMaxWidth().padding(horizontal = 4.dp).heightIn(max = maxHeight)
                    .subtitleZoom(scaleSize, onZoomEnd = saveSize, scale = scaleSize),
                finalOnly = state.visuals.finalOnly, reducedMotion = state.visuals.reducedMotion, animateReflow = state.visuals.reflow)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CaptionTool(label: String, symbol: String, fallback: ImageVector, action: () -> Unit) {
    val radius = LocalCornerRadius.current.coerceAtMost(16.dp)
    val corners = connectedShape(outer = radius)
    ToolButton(label, MaterialSymbols.vector(symbol) ?: fallback, action,
        shapes = IconButtonDefaults.shapes(corners, connectedShape(pressed = true, outer = radius)))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CaptionWindowActions(active: Boolean, toggle: () -> Unit, close: () -> Unit) {
    val start = remember { MutableInteractionSource() }; val end = remember { MutableInteractionSource() }
    val startPressed by start.collectIsPressedAsState(); val endPressed by end.collectIsPressedAsState()
    val radius = LocalCornerRadius.current.coerceAtMost(16.dp)
    @Composable fun shape(neighbors: CornerNeighbors, adjacentPressed: Boolean) = IconButtonDefaults.shapes(
        connectedShape(neighbors, neighborPressed = adjacentPressed, outer = radius),
        connectedShape(neighbors, pressed = true, outer = radius))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolButton(if (active) "停止聆听" else "开始聆听",
            if (active) MaterialSymbols.vector("stop") ?: Icons.Default.Stop else MaterialSymbols.vector("mic") ?: Icons.Default.Mic,
            toggle, interactionSource = start, shapes = shape(CornerNeighbors(end = true), endPressed))
        ToolButton("关闭字幕浮窗", MaterialSymbols.vector("close") ?: Icons.Default.Close, close,
            interactionSource = end, shapes = shape(CornerNeighbors(start = true), startPressed))
    }
}
