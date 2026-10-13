package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kigtts.next.R
import com.kigtts.next.state.CardPrintRequest
import com.kigtts.next.state.CardPrintOrigin
import com.kigtts.next.ui.cards.PolaroidCanvas
import com.kigtts.next.ui.cards.cardImageDecodeSize
import com.kigtts.next.ui.cards.polaroidFormat
import com.kigtts.next.ui.cards.rememberCardImage
import com.kigtts.plugin.api.PolaroidFrameStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** A physical paper feed: only the paper's position changes, with no repeated photo layout or decoding. */
@Composable
internal fun PolaroidPrinter(request: CardPrintRequest, imageFile: (String) -> File?,
    frame: PolaroidFrameStyle?, active: Boolean, finished: () -> Boolean,
    previewHandoff: ((CardPrintOrigin) -> Boolean)? = null) {
    var savedHousing by rememberSaveable(request.requestId) { mutableFloatStateOf(0f) }
    var savedFeed by rememberSaveable(request.requestId) { mutableFloatStateOf(0f) }
    var delivered by rememberSaveable(request.requestId) { mutableStateOf(false) }
    val housing = remember(request.requestId) { Animatable(savedHousing) }
    val feed = remember(request.requestId) { Animatable(savedFeed) }
    val complete = rememberUpdatedState(finished)
    val handoff = rememberUpdatedState(previewHandoff)
    val live = rememberUpdatedState(active)
    var paperCoordinates by remember(request.requestId) { mutableStateOf<LayoutCoordinates?>(null) }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).testTag("polaroid-printer")) {
        val density = LocalDensity.current
        val inset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        val slot = inset + 100.dp
        val ratio = polaroidFormat(request.card, frame).geometry.aspectRatio.toFloat()
        val height = (maxHeight - slot - bottom - 24.dp).coerceAtLeast(1.dp)
        val width = minOf(maxWidth * .78f, height * ratio, 460.dp).coerceAtLeast(1.dp)
        val paperHeight = width / ratio
        val decoded = rememberCardImage(request.card.imageId?.let(imageFile),
            cardImageDecodeSize(with(density) { width.toPx() }, 1200))
        val ready = rememberUpdatedState(decoded != null || request.card.imageId == null)
        LaunchedEffect(request.requestId) {
            housing.animateTo(1f, tween((240 * (1f - housing.value)).toInt().coerceAtLeast(1),
                easing = FastOutSlowInEasing)) { savedHousing = value }
            // The first visible part of the photograph must already have pixels; the source is durable.
            withTimeoutOrNull(3000) { snapshotFlow { ready.value }.first { it } }
            feed.animateTo(1f, tween((1500 * (1f - feed.value)).toInt().coerceAtLeast(1),
                easing = LinearEasing)) { savedFeed = value }
            delay(160)
            while (!delivered) {
                snapshotFlow { live.value }.first { it }
                val prepared = handoff.value?.let { prepare ->
                    paperCoordinates?.takeIf { it.isAttached }?.let { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        val viewport = coordinates.findRootCoordinates().size
                        prepare(CardPrintOrigin(bounds.left, bounds.top, bounds.width, bounds.height,
                            viewport.width.toFloat(), viewport.height.toFloat()))
                    } ?: false
                } ?: complete.value()
                if (prepared) delivered = true
                else withFrameNanos { }
            }
        }
        Box(Modifier.fillMaxSize().drawWithContent {
            clipRect(top = slot.toPx()) { this@drawWithContent.drawContent() }
        }) {
            PolaroidCanvas(request.card, imageFile, Modifier.align(Alignment.TopCenter)
                .offset(y = slot - paperHeight).width(width).graphicsLayer {
                    translationY = (paperHeight + 16.dp).toPx() * feed.value
                }.onGloballyPositioned { paperCoordinates = it }.testTag("printer-paper"), frame = frame)
        }
        Surface(Modifier.fillMaxWidth().height(slot + 8.dp).graphicsLayer {
            translationY = -(slot + 8.dp).toPx() * (1f - housing.value)
        }.testTag("printer-housing"), shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color = colors.surfaceContainerHigh, shadowElevation = 12.dp) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                colors.surfaceContainerHighest, colors.surfaceContainerHigh, colors.surfaceContainer)))) {
                Icon(painterResource(R.drawable.logo_horizontal), "KIGTTS 打印机",
                    Modifier.align(Alignment.Center).padding(top = inset).width(152.dp).aspectRatio(4f),
                    tint = colors.onSurface)
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 8.dp)
                    .fillMaxWidth().height(6.dp).background(colors.onSurface.copy(alpha = .82f), RoundedCornerShape(50))
                    .testTag("printer-slot"))
            }
        }
    }
}
