package com.kigtts.next.ui.cards.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.navigation.RegisterCameraPullGuard

/** Saving, camera suspension and printing stay in the native camera scene until its paper is delivered. */
@Composable
fun PolaroidCameraRoute(model: AppViewModel, destination: Destination.CardCamera) {
    val request by model.cardCapture.printRequest.collectAsStateWithLifecycle()
    val current = request?.takeIf { it.requestId == destination.requestId }
    var retained by remember(destination.requestId) { mutableStateOf<com.kigtts.next.state.CardPrintRequest?>(null) }
    if (current != null) retained = current
    // Navigation retains the outgoing camera: keep its printed paper through that exit, rather than reveal a stopped preview.
    val print = current ?: retained
    val active = LocalPageActive.current
    RegisterCameraPullGuard(print != null)
    Box(Modifier.fillMaxSize()) {
        PageScaffold("拍摄", model::navigateBack, largeBar = false, subtitle = "上滑返回相册") {
            CardCameraScreen(
                onCapture = { shot -> model.cardCapture.captured(destination, shot.original, shot.displayFile,
                    shot.format, print = destination.cardId == null) },
                onCancel = model::navigateBack, message = model::message,
                initialFormat = destination.cardId?.let { id -> model.cards.cards.value.firstOrNull { it.id == id } }
                    ?.let { model.stickers.frameStyle(it).format }, suspended = print != null)
        }
        if (print != null) PolaroidPrinter(print, model.stickers::imageFile,
            model.stickers.frameStyle(print.card), active, { model.cardCapture.finishPrint(print.requestId) })
    }
}
