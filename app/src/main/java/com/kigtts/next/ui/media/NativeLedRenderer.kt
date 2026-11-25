package com.kigtts.next.ui.media

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.theme.LocalAppearanceFont
import com.kigtts.next.ui.components.LocalModalWindows
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.NativeSubtitleRenderer.Led
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun NativeLedRenderer(style: Led, text: String, placeholder: String, percent: Float, safeArea: MediaSafeArea,
    reducedMotion: Boolean, onQuickSwipe: (Boolean) -> Unit, zoomPreview: () -> Float, modifier: Modifier = Modifier) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = LocalPageActive.current && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val callback by rememberUpdatedState(onQuickSwipe)
    SubtitleRendererWindow(RendererWindowSettings(style.keepScreenOn, style.screenBrightness == null,
        style.screenBrightness ?: 1f, style.quickSwipe), active)
    DisposableEffect(active, style.quickSwipe) {
        callback(active && style.quickSwipe)
        onDispose { callback(false) }
    }

    var board by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density
    val font by LocalFontFamilyResolver.current.resolve(LocalAppearanceFont.current.family, FontWeight.Bold)
    val typeface = font as? Typeface ?: Typeface.DEFAULT_BOLD
    val displayed = text.ifBlank { placeholder }
    val rasterStyle = remember(style) { style.copy(quickSwipe = false, keepScreenOn = false, screenBrightness = null) }
    val raster by produceState<LedRaster?>(null, displayed, board, safeArea, percent, density, rasterStyle, typeface) {
        value = withContext(Dispatchers.Default) { rasterLed(displayed, board, safeArea, percent, density, rasterStyle, typeface) }
    }
    val offset = remember { mutableFloatStateOf(0f) }
    val gap = style.loopGap * density
    val scrollable = (raster?.distance ?: 0f) > 0f
    val scrollActive = active && (LocalModalWindows.current?.count ?: 0) == 0
    LaunchedEffect(displayed, style.wrap, style.scrollRight) { offset.floatValue = 0f }
    LaunchedEffect(displayed, rasterStyle, scrollActive, reducedMotion, scrollable, gap) {
        if (!scrollable || !scrollActive) return@LaunchedEffect
        if (reducedMotion) {
            while (true) {
                delay(6_000)
                val period = raster?.period(gap) ?: 0f
                val step = (if (style.wrap) board.height - safeArea.top - safeArea.bottom
                    else board.width - safeArea.left - safeArea.right).coerceAtLeast(1) * .85f
                val position = offset.floatValue
                offset.floatValue = if (position >= period) 0f else (position + step).coerceAtMost(period)
            }
        } else {
            delay(1_200)
            var previous = withFrameNanos { it }
            while (true) withFrameNanos { frame ->
                val elapsed = ((frame - previous) / 1_000_000_000.0).toFloat()
                previous = frame
                if (zoomPreview().isFinite()) return@withFrameNanos
                val period = raster?.period(gap)?.takeIf { it > 0f } ?: return@withFrameNanos
                val travel = if (style.wrap) period + style.scrollSpeed * density * .8f else period
                offset.floatValue = (offset.floatValue + elapsed * style.scrollSpeed * density) % travel
            }
        }
    }
    Canvas(modifier.clipToBounds().onSizeChanged { board = it }.testTag("native-led-renderer")
        .semantics { contentDescription = displayed }) {
        val scene = raster
        if (scene == null) drawRect(Color(style.background.toInt()))
        else {
            val drawPercent = zoomPreview().takeIf(Float::isFinite) ?: percent
            drawIntoCanvas { scene.draw(it.nativeCanvas, offset.floatValue, gap, drawPercent) }
        }
    }
}
