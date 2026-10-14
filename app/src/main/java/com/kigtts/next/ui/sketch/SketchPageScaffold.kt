package com.kigtts.next.ui.sketch

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.*

internal class SketchChrome { var drawing by mutableStateOf(true) }
internal val LocalSketchChrome = compositionLocalOf<SketchChrome?> { null }

/** Chrome floats over a full-size drawable surface; its empty space never intercepts the pen. */
@Composable fun SketchPageScaffold(back: () -> Unit, share: () -> Unit, content: @Composable () -> Unit) {
    val host = remember { PageToolbarActions() }; val chrome = remember { SketchChrome() }
    val active = LocalPageActive.current
    SketchFullscreen(active && chrome.drawing)
    CompositionLocalProvider(LocalPageToolbarActions provides host, LocalPageBackAction provides back, LocalSketchChrome provides chrome) {
        Box(Modifier.fillMaxSize().testTag("sketch-fullscreen-page")) {
            content()
            if (chrome.drawing) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 4.dp).testTag("sketch-header"),
                verticalAlignment = Alignment.CenterVertically) {
                MotionIconButton(feedbackClick { if (host.backGuard?.value?.invoke() != true) back() },
                    Modifier.testTag("page-back"), enabled = active, colors = compactPlainIconColors()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回大字幕")
                }
                Text("画板", Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
                PageActionButtons(host.source?.value.orEmpty() + ToolbarAction("share-sketch", "分享画板", Icons.Default.Share, active, click = share))
            }
        }
    }
}
