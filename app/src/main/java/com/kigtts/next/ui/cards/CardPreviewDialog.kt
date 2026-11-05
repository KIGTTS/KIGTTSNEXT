package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.kigtts.next.state.CardPrintOrigin
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File

/** The paper floats directly above the dimmed page; primary actions stay along the bottom. */
@Composable
internal fun CardPreviewDialog(card: PolaroidCard, imageFile: (String) -> File?, frame: PolaroidFrameStyle?,
    onDismiss: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onQuick: () -> Unit,
    onAction: (CardMenuAction) -> Unit, onMove: () -> Unit, enabled: Boolean = true,
    printedOrigin: CardPrintOrigin? = null, onFirstPaperDraw: () -> Unit = {}, printedAcknowledged: Boolean = false,
    readOnly: Boolean = false, motion: CardMotionPreview? = null) {
    var menu by remember(card.id) { mutableStateOf(false) }
    val viewer = remember(card.id) { PolaroidPreviewTransform() }
    val showReset by remember(viewer) { derivedStateOf { viewer.zoom > 1.001f } }
    // Sample ACK only when this composition is created. A live ACK must not interrupt its
    // original morph; an already-ACKed window recreated by rotation resumes the final preview.
    val resumedPreview = remember(card.id) { printedAcknowledged }
    val initialHandoff = printedOrigin != null && !resumedPreview
    var paperReady by remember(card.id) { mutableStateOf(!initialHandoff) }
    var paperDrawn by remember(card.id) { mutableStateOf(false) }
    var handoffComplete by rememberSaveable(card.id) { mutableStateOf(printedOrigin == null) }
    val drawn = rememberUpdatedState(onFirstPaperDraw)
    val faceShare = com.kigtts.next.sharing.LocalFaceShare.current
    val receipt = com.kigtts.next.sharing.rememberFaceReceipt(card.id)
    var senderInfo by remember(card.id) { mutableStateOf(false) }
    CardPreviewWindow(onDismiss, enterReady = paperReady && (!initialHandoff || paperDrawn), printHandoff = initialHandoff,
        closeEnabled = paperReady && (!initialHandoff || handoffComplete), initiallyOpened = resumedPreview) { reveal, closing, close ->
        LaunchedEffect(reveal) { if (reveal >= .999f) handoffComplete = true }
        val movingPaper = initialHandoff && !handoffComplete && !closing
        val controlsEnabled = enabled && !closing && paperReady && (!initialHandoff || handoffComplete)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
            .testTag("card-preview-dialog"), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var viewport by remember(card.id) { mutableStateOf(Rect.Zero) }
            var window by remember(card.id) { mutableStateOf(IntSize.Zero) }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)
                .then(if (movingPaper) Modifier else Modifier.clipToBounds())
                .onSizeChanged { viewer.viewportSize = it }
                .onGloballyPositioned { viewport = it.boundsInWindow(); window = it.findRootCoordinates().size }
                .polaroidPreviewGestures(viewer, controlsEnabled && reveal >= .999f, feedbackClick { close(onDismiss) },
                    onSwipeUp = faceShare?.let { { it.send(listOf(card.id)) } })
                .testTag("card-preview-content"), contentAlignment = Alignment.Center) {
                val width = minOf(maxWidth, maxHeight * com.kigtts.next.cards.polaroidPaperGeometry(card, frame).aspectRatio.toFloat(), 720.dp)
                val density = LocalDensity.current
                val warmImage = if (printedOrigin != null) {
                    val file = card.imageId?.let(imageFile)
                    val target = rememberCardImage(file, cardImageDecodeSize(with(density) { width.toPx() }, 1200))
                    // The printer's decode is already available. Keep those pixels visible while
                    // the larger preview bucket loads, instead of waiting with a blank paper.
                    val source = rememberCardImage(file, cardImageDecodeSize(printedOrigin.width, 1200))
                    target ?: source
                } else null
                val ready = !initialHandoff || (viewport.width > 0f && (card.imageId == null || warmImage != null))
                SideEffect { if (ready) paperReady = true }
                Box(Modifier.width(width.coerceAtLeast(1.dp)).aspectRatio(com.kigtts.next.cards.polaroidPaperGeometry(card, frame).aspectRatio.toFloat())
                    .onSizeChanged { viewer.paperSize = it }.graphicsLayer {
                    if (movingPaper) {
                        val pose = printedPaperPose(printedPaperStart(requireNotNull(printedOrigin), window),
                            viewport.center, size.width, reveal)
                        alpha = if (ready) 1f else 0f
                        scaleX = pose.scale; scaleY = pose.scale
                        translationX = pose.translation.x; translationY = pose.translation.y
                    } else {
                        alpha = reveal
                        scaleX = (.96f + .04f * reveal) * viewer.zoom; scaleY = scaleX
                        translationX = viewer.pan.x
                        translationY = viewer.pan.y + (1f - reveal) * 24.dp.toPx()
                    }
                }.drawWithContent {
                    drawContent()
                    // ACK is emitted only after the full paper, including its warmed image, drew.
                    if (initialHandoff && ready && !paperDrawn) {
                        paperDrawn = true
                        // The native view queue runs after this actual draw, without depending
                        // on an unrelated recomposition or a newly advanced animation frame.
                        Handler(Looper.getMainLooper()).post { drawn.value() }
                    }
                }) {
                    // Promote only when a zoom bucket changes, not on every pinch event.
                    val imageScale by remember(viewer) { derivedStateOf { kotlin.math.ceil(viewer.zoom * 2f) / 2f } }
                    PolaroidCanvas(card, imageFile, Modifier.fillMaxSize(), frame = frame, photoOverride = warmImage,
                        decodeMaximum = 4096, viewScale = imageScale)
                    motion?.let { CardMotionVideo(it, controlsEnabled && reveal >= .999f, Modifier.fillMaxSize()) }
                }
            }
            motion?.let { CardMotionControls(it, controlsEnabled, Modifier.widthIn(max = 480.dp).fillMaxWidth().graphicsLayer { alpha = reveal }) }
            Row(Modifier.widthIn(max = 480.dp).fillMaxWidth().graphicsLayer { alpha = reveal }
                .testTag("card-preview-actions"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardPreviewButton("分享", Icons.Default.Share, feedbackClick { faceShare?.send(listOf(card.id)) ?: onShare() }, Modifier.weight(1f).testTag("share-card-preview"),
                controlsEnabled, closing)
            if (!readOnly) CardPreviewButton("编辑", Icons.Default.Edit, feedbackClick { close(onEdit) }, Modifier.weight(1f).testTag("edit-card-preview"),
                controlsEnabled, closing, primary = true)
            Box {
                MotionIconButton(feedbackClick { menu = true }, enabled = controlsEnabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Icon(Icons.Default.MoreVert, "照片操作")
                }
                DropdownMenu(menu && controlsEnabled, { menu = false }) {
                    if (receipt != null) DropdownMenuItem({ Text("发送者名片") }, onClick = feedbackClick { menu = false; senderInfo = true },
                        leadingIcon = { Icon(Icons.Default.AccountCircle, null) })
                    DropdownMenuItem({ Text("添加到快捷文本") }, onClick = feedbackClick { menu = false; close(onQuick) },
                        leadingIcon = { Icon(Icons.Default.TextFields, null) })
                    DropdownMenuItem({ Text("移到相册") }, onClick = feedbackClick { menu = false; close(onMove) },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, null) })
                    CardMenuAction.entries.filter { it != CardMenuAction.QUICK }.forEach { operation ->
                        DropdownMenuItem({ Text(operation.label) }, onClick = if (operation == CardMenuAction.DELETE)
                            feedbackClick { menu = false; close { onAction(operation) } }
                            else feedbackClick { menu = false; onAction(operation) },
                            leadingIcon = { Icon(when (operation) {
                                CardMenuAction.SAVE -> Icons.Default.Download
                                CardMenuAction.COPY -> Icons.Default.ContentCopy
                                CardMenuAction.QUICK -> Icons.Default.TextFields
                                CardMenuAction.DELETE -> Icons.Default.DeleteOutline
                            }, null) })
                    }
                }
            }
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)
            .graphicsLayer { alpha = reveal }, contentAlignment = Alignment.TopEnd) {
            Row {
            AnimatedVisibility(showReset, enter = fadeIn(), exit = fadeOut()) {
                MotionIconButton(feedbackClick(viewer::reset), Modifier.testTag("reset-card-preview"), enabled = controlsEnabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Icon(Icons.Default.CenterFocusStrong, "重置缩放")
                }
            }
            MotionIconButton(feedbackClick { close(onDismiss) }, Modifier.testTag("close-card-preview"), enabled = controlsEnabled,
                colors = IconButtonDefaults.iconButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Icon(Icons.Default.Close, "关闭预览") }
            }
        }
    }
    if (senderInfo && receipt != null) com.kigtts.next.sharing.FaceReceiptDialog(receipt) { senderInfo = false }
}
