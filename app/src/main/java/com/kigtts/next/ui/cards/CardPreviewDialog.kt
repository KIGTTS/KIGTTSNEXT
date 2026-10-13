package com.kigtts.next.ui.cards

import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.*
import java.io.File

/** The paper floats directly above the dimmed page; primary actions stay along the bottom. */
@Composable
internal fun CardPreviewDialog(card: PolaroidCard, imageFile: (String) -> File?, frame: PolaroidFrameStyle?,
    onDismiss: () -> Unit, onEdit: () -> Unit, onShare: () -> Unit, onQuick: () -> Unit,
    onAction: (CardMenuAction) -> Unit, onMove: () -> Unit, enabled: Boolean = true) {
    var menu by remember(card.id) { mutableStateOf(false) }
    val viewer = remember(card.id) { PolaroidPreviewTransform() }
    val showReset by remember(viewer) { derivedStateOf { viewer.zoom > 1.001f } }
    CardPreviewWindow(onDismiss) { reveal, closing, close ->
        val controlsEnabled = enabled && !closing
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)
            .testTag("card-preview-dialog"), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged { viewer.viewportSize = it }
                .polaroidPreviewGestures(viewer, !closing && reveal >= .999f, feedbackClick { close(onDismiss) })
                .testTag("card-preview-content"), contentAlignment = Alignment.Center) {
                val width = minOf(maxWidth, maxHeight * polaroidFormat(card, frame).geometry.aspectRatio.toFloat(), 720.dp)
                PolaroidCanvas(card, imageFile, Modifier.width(width.coerceAtLeast(1.dp)).onSizeChanged { viewer.paperSize = it }.graphicsLayer {
                    alpha = reveal
                    scaleX = (.96f + .04f * reveal) * viewer.zoom; scaleY = scaleX
                    translationX = viewer.pan.x
                    translationY = viewer.pan.y + (1f - reveal) * 24.dp.toPx()
                }, frame = frame)
            }
            Row(Modifier.widthIn(max = 480.dp).fillMaxWidth().graphicsLayer { alpha = reveal }
                .testTag("card-preview-actions"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardPreviewButton("分享", Icons.Default.Share, feedbackClick(onShare), Modifier.weight(1f).testTag("share-card-preview"),
                controlsEnabled, closing)
            CardPreviewButton("编辑", Icons.Default.Edit, feedbackClick { close(onEdit) }, Modifier.weight(1f).testTag("edit-card-preview"),
                controlsEnabled, closing, primary = true)
            Box {
                MotionIconButton(feedbackClick { menu = true }, enabled = controlsEnabled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Icon(Icons.Default.MoreVert, "拍立得操作")
                }
                DropdownMenu(menu && controlsEnabled, { menu = false }) {
                    DropdownMenuItem({ Text("添加到快捷文本") }, onClick = feedbackClick { menu = false; onQuick() },
                        leadingIcon = { Icon(Icons.Default.TextFields, null) })
                    DropdownMenuItem({ Text("移到相册") }, onClick = feedbackClick { menu = false; close(onMove) },
                        leadingIcon = { Icon(Icons.Default.DriveFileMove, null) })
                    CardMenuAction.entries.filter { it != CardMenuAction.QUICK }.forEach { operation ->
                        DropdownMenuItem({ Text(operation.label) }, onClick = if (operation == CardMenuAction.DELETE)
                            feedbackClick { menu = false; close { onAction(operation) } }
                            else feedbackClick { menu = false; onAction(operation) },
                            leadingIcon = { Icon(when (operation) {
                                CardMenuAction.COPY -> Icons.Default.ContentCopy
                                CardMenuAction.QUICK -> Icons.Default.TextFields
                                CardMenuAction.DELETE -> Icons.Default.DeleteOutline
                            }, null) })
                    }
                }
            }
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp), contentAlignment = Alignment.TopEnd) {
            Row {
            AnimatedVisibility(showReset, enter = fadeIn(), exit = fadeOut()) {
                MotionIconButton(feedbackClick(viewer::reset), Modifier.testTag("reset-card-preview"), enabled = !closing,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) {
                    Icon(Icons.Default.CenterFocusStrong, "重置缩放")
                }
            }
            MotionIconButton(feedbackClick { close(onDismiss) }, Modifier.testTag("close-card-preview"), enabled = !closing,
                colors = IconButtonDefaults.iconButtonColors(contentColor = androidx.compose.ui.graphics.Color.White)) { Icon(Icons.Default.Close, "关闭预览") }
            }
        }
    }
}
