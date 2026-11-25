package com.kigtts.next.ui.media

import android.graphics.Typeface
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
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
import com.kigtts.next.state.LedPlayback
import com.kigtts.next.ui.components.LocalModalWindows
import com.kigtts.next.ui.home.preferredSubtitleFontSize
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.plugin.api.NativeSubtitleRenderer.Led
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun NativeLedRenderer(style: Led, text: String, placeholder: String, percent: Float, safeArea: MediaSafeArea,
    reducedMotion: Boolean, playback: LedPlayback, zoomPreview: () -> Float, modifier: Modifier = Modifier) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = LocalPageActive.current && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    var board by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val fullSize = preferredSubtitleFontSize(100f)
    val fontWidth = with(density) { fullSize.toPx() }
    val font by LocalFontFamilyResolver.current.resolve(LocalAppearanceFont.current.family, FontWeight.Bold)
    val typeface = font as? Typeface ?: Typeface.DEFAULT_BOLD
    val displayed = text.ifBlank { placeholder }
    val rasterStyle = remember(style) { style.copy(fontScale = 1f, quickSwipe = false, keepScreenOn = false, screenBrightness = null) }
    val raster by produceState<LedRaster?>(null, displayed, board, safeArea, percent, density.density, rasterStyle, typeface, fontWidth) {
        if (value?.board?.let { it != board } == true) delay(80)
        value = withContext(Dispatchers.Default) {
            rasterLed(displayed, board, safeArea, percent, density.density, rasterStyle, typeface, fontWidth)
        }
    }
    val gap = style.loopGap * density.density
    val range = raster?.period(gap, board, safeArea, percent) ?: 0f
    val scrollable = range > 0f
    val scrollActive = active && (LocalModalWindows.current?.count ?: 0) == 0
    SideEffect { playback.layout(range, loop = !style.wrap, right = !style.wrap && style.scrollRight) }
    LaunchedEffect(displayed, style.wrap, style.scrollRight) { playback.restart() }
    val currentPageStep by rememberUpdatedState(if (style.wrap) board.height - safeArea.top - safeArea.bottom
        else board.width - safeArea.left - safeArea.right)
    LaunchedEffect(displayed, rasterStyle, scrollActive, reducedMotion, scrollable, density.density) {
        if (!scrollable || !scrollActive) return@LaunchedEffect
        if (reducedMotion) {
            while (true) {
                delay(6_000)
                playback.page(currentPageStep.coerceAtLeast(1) * .85f)
            }
        } else {
            var previous = withFrameNanos { it }
            while (true) withInfiniteAnimationFrameNanos { frame ->
                val elapsed = ((frame - previous) / 1_000_000_000.0).toFloat().coerceIn(0f, .06f)
                previous = frame
                if (!zoomPreview().isFinite()) playback.tick(elapsed, style.scrollSpeed * density.density)
            }
        }
    }
    Canvas(modifier.clipToBounds().onSizeChanged { board = it }.testTag("native-led-renderer")
        .semantics { contentDescription = displayed }) {
        val scene = raster
        if (scene == null) drawRect(Color(style.background.toInt()))
        else {
            val drawPercent = zoomPreview().takeIf(Float::isFinite) ?: percent
            val bounds = IntSize(size.width.roundToInt(), size.height.roundToInt())
            drawIntoCanvas { scene.draw(it.nativeCanvas, playback.offset, gap, drawPercent, bounds, safeArea) }
        }
    }
}
