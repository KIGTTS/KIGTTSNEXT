@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.sharing

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable
internal fun FaceShareHost(model: AppViewModel) {
    val controller = model.faceShare
    val panel by controller.panel.collectAsStateWithLifecycle()
    val requests by controller.requests.collectAsStateWithLifecycle()
    val busy by controller.busy.collectAsStateWithLifecycle()
    val status by controller.status.collectAsStateWithLifecycle()
    val progress by controller.progress.collectAsStateWithLifecycle()
    val url by controller.url.collectAsStateWithLifecycle()
    val hotspot by controller.hotspot.details.collectAsStateWithLifecycle()
    val peers by controller.peers.collectAsStateWithLifecycle()
    val receiving by controller.receiving.collectAsStateWithLifecycle()
    val identities by model.identities.items.collectAsStateWithLifecycle()
    val catalog by model.stickers.snapshot.collectAsStateWithLifecycle()
    val permission = rememberFacePermissionRequest(model::message)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.foreground(false)
            if (event == Lifecycle.Event.ON_START) controller.foreground(true)
        }
        controller.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(panel?.mode, panel?.ids, receiving) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.startDiscovery()
    }
    panel?.let { shown ->
        var format by remember(shown.ids) { mutableStateOf(FacePhotoFormat.COMPOSED) }
        FaceShareSheet(if (shown.mode == FaceMode.BROWSER) "Wi-Fi 互传" else if (shown.personal) "分享名片" else "分享照片", controller::hide,
            maximumHeight = if (shown.mode == FaceMode.SEND) { if (shown.personal) 420.dp else 520.dp } else 580.dp,
            back = if (shown.mode == FaceMode.BROWSER) controller::returnToShare else null,
            finish = if (shown.mode == FaceMode.BROWSER) controller::finishSending else null) {
            AnimatedContent(shown.mode, Modifier.fillMaxWidth(), transitionSpec = {
                val direction = if (targetState == FaceMode.BROWSER) 1 else -1
                (fadeIn(tween(200, delayMillis = 40)) + slideInHorizontally(tween(240)) { direction * it / 12 })
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -direction * it / 12 }).using(null)
            }, label = "face-share-page") { mode ->
              Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    FaceMode.SEND -> {
                        FaceSharePreview(if (shown.personal) identities.firstOrNull() else null,
                            shown.ids.mapNotNull { id -> catalog.cards.firstOrNull { it.id == id } }, model.stickers)
                        if (!shown.personal) FacePhotoFormatChoice(format) { if (!busy) format = it }
                        Spacer(Modifier.height(4.dp))
                        FaceDiscoveryBar(peers, busy,
                            share = { controller.shareSystem(format.original, format.composed) },
                            wifi = { controller.prepareBrowser(format.original, format.composed) },
                            send = { controller.send(it, format.original, format.composed) })
                        if (peers.isEmpty() && !busy) FaceDiscoveryHelp { permission(true, controller::refreshDiscovery) }
                    }
                    FaceMode.BROWSER -> {
                        FaceWifiShareContent(hotspot, url, shown.personal) { permission(false, controller::startHotspot) }
                    }
                }
              }
            }
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
    FaceReceiveBanner(requests.firstOrNull(), controller::approve, controller::reject)
}
