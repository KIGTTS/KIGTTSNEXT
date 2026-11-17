package com.kigtts.next.ui.cards.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.launch

@Composable
internal fun CameraBurstPreview(photos: List<PolaroidCameraCapture>, initial: Int, selected: Set<Int>,
    busy: Boolean, close: () -> Unit, toggle: (Int) -> Unit) {
    val copy = rememberCameraCopy()
    val pager = rememberPagerState(initialPage = initial) { photos.size }
    val scope = rememberCoroutineScope()
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)).testTag("burst-preview")) {
            HorizontalPager(pager, Modifier.fillMaxSize().safeDrawingPadding().padding(top = 64.dp, bottom = 56.dp)
                .testTag("burst-preview-pager"), key = { photos[it].original.path }) { index ->
                BurstPhotoImage(photos[index], Modifier.fillMaxSize(), crop = false, maximum = 1600)
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                PreviewArrow(copy.text("camera.close_preview"), Icons.Default.Close, close)
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White,
                    modifier = Modifier.testTag("burst-preview-count"))
                Spacer(Modifier.weight(1f))
                BurstPhotoCheck(pager.currentPage in selected, !busy, { toggle(pager.currentPage) },
                    Modifier.testTag("burst-preview-check"))
            }
            AnimatedVisibility(pager.currentPage > 0, Modifier.align(Alignment.CenterStart), enter = fadeIn(), exit = fadeOut()) {
                PreviewArrow(copy.text("camera.previous_photo"), Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } })
            }
            AnimatedVisibility(pager.currentPage < photos.lastIndex, Modifier.align(Alignment.CenterEnd), enter = fadeIn(), exit = fadeOut()) {
                PreviewArrow(copy.text("camera.next_photo"), Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(photos.lastIndex)) } })
            }
        }
    }
}

@Composable
private fun PreviewArrow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    IconActionHint(label, false) {
        IconButton(feedbackClick(click), Modifier.size(52.dp)) { Icon(icon, label, Modifier.size(32.dp), tint = Color.White) }
    }
}
