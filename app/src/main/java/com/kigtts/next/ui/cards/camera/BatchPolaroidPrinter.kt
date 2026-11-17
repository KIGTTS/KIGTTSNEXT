package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.CardPrintRequest
import com.kigtts.next.ui.cards.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.random.Random

internal fun batchPrintRotation(seed: Int, ordinal: Int, feed: Float, settle: Float): Float =
    if (feed < 1f) 0f else (Random(seed xor (ordinal * 7919)).nextFloat() * 6f - 3f) * settle

/** Fast eased feeds remain straight inside the slot; only fully ejected sheets tilt into the stack. */
@Composable
internal fun BatchPolaroidPrinter(request: CardPrintRequest, imageFile: (String) -> File?,
    frame: (PolaroidCard) -> PolaroidFrameStyle?, active: Boolean, finished: () -> Boolean) {
    val copy = rememberCameraCopy()
    var completed by rememberSaveable(request.requestId) { mutableIntStateOf(0) }
    var savedFeed by rememberSaveable(request.requestId) { mutableFloatStateOf(0f) }
    var savedSettle by rememberSaveable(request.requestId) { mutableFloatStateOf(0f) }
    var feedOwner by rememberSaveable(request.requestId) { mutableIntStateOf(0) }
    var delivered by rememberSaveable(request.requestId) { mutableStateOf(false) }
    val feed = remember(request.requestId) { Animatable(savedFeed) }
    val settle = remember(request.requestId) { Animatable(savedSettle) }
    val housing = remember(request.requestId) { Animatable(0f) }
    val live = rememberUpdatedState(active); val finish = rememberUpdatedState(finished)
    val current = request.cards[completed.coerceAtMost(request.cards.lastIndex)]
    val ready = rememberUpdatedState(current.id to (rememberCardImage(current.imageId?.let(imageFile), 1200) != null || current.imageId == null))
    LaunchedEffect(request.requestId) {
        housing.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        while (completed < request.cards.size) {
            val expected = request.cards[completed].id
            snapshotFlow { live.value }.first { it }
            withFrameNanos { }
            withTimeoutOrNull(3000) { snapshotFlow { ready.value }.first { it.first == expected && it.second } }
            feed.animateTo(1f, tween((420 * (1f - feed.value)).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing)) { savedFeed = value }
            settle.animateTo(1f, tween((140 * (1f - settle.value)).toInt().coerceAtLeast(1), easing = LinearOutSlowInEasing)) { savedSettle = value }
            // Settle the old sheet before adding the next; no frame ever teleports a half-fed photograph.
            completed++; feed.snapTo(0f); settle.snapTo(0f); savedFeed = 0f; savedSettle = 0f; feedOwner = completed
        }
        delay(400)
        while (!delivered) {
            snapshotFlow { live.value }.first { it }
            delivered = finish.value()
            if (!delivered) withFrameNanos { }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("batch-printer")) {
        val availableWidth = maxWidth; val availableHeight = maxHeight
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val slot = insets.calculateTopPadding() + 100.dp
        val seed = request.requestId.hashCode()
        val first = (completed - 4).coerceAtLeast(0)
        val last = completed.coerceAtMost(request.cards.lastIndex)
        Box(Modifier.fillMaxSize().drawWithContent { clipRect(top = slot.toPx()) { this@drawWithContent.drawContent() } }) {
            for (index in first..last) key(request.cards[index].id) {
                val card = request.cards[index]; val style = frame(card)
                val ratio = polaroidFormat(card, style).geometry.aspectRatio.toFloat()
                val height = (availableHeight - slot - insets.calculateBottomPadding() - 40.dp).coerceAtLeast(1.dp)
                val width = minOf(availableWidth * .74f, height * ratio, 420.dp).coerceAtLeast(1.dp)
                val paperHeight = width / ratio
                val isCurrent = index == completed
                PolaroidCanvas(card, imageFile, Modifier.align(Alignment.TopCenter).offset(y = slot - paperHeight).width(width)
                    .graphicsLayer {
                        val progress = if (!isCurrent) 1f else if (feedOwner == index) feed.value else 0f
                        val settled = if (!isCurrent) 1f else if (feedOwner == index) settle.value else 0f
                        translationY = (paperHeight + 16.dp).toPx() * progress + (index % 5 * 3).dp.toPx() * settled
                        translationX = ((index % 3 - 1) * 4).dp.toPx() * settled
                        rotationZ = batchPrintRotation(seed, index, progress, settled)
                    }.testTag("batch-paper/$index"), frame = style)
            }
        }
        PrinterHousing(slot, insets.calculateTopPadding(), housing.value)
        Text(copy.text("camera.burst_saving", "done" to minOf(completed + 1, request.cards.size).toString(), "total" to request.cards.size.toString()),
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).testTag("batch-print-count"),
            style = MaterialTheme.typography.labelLarge)
    }
}
