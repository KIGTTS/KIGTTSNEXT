package com.kigtts.next.ui.cards.camera

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterCameraPullGuard
import com.kigtts.next.ui.cards.rememberCardPhotoSources
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** Saving, camera suspension and printing stay in the native camera scene until its paper is delivered. */
@Composable
fun PolaroidCameraRoute(model: AppViewModel, destination: Destination.CardCamera) {
    val request by model.cardCapture.printRequest.collectAsStateWithLifecycle()
    val copying by model.cardCapture.processing.collectAsStateWithLifecycle()
    var importing by remember(destination.requestId) { mutableStateOf(false) }
    var galleryFormat by rememberSaveable(destination.requestId) { mutableStateOf(PolaroidFormat.SQUARE) }
    val scope = rememberCoroutineScope()
    val picker = rememberCardPhotoSources({ selection ->
        if (importing || copying || !scope.isActive) runCatching(selection.release)
        else {
            importing = true
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var shot: PolaroidCameraCapture? = null
                var delivered = false
                try {
                    val selected = cameraGalleryCapture(model.getApplication<Application>(), selection.uri, galleryFormat)
                    shot = selected.photo
                    if (model.state.value.destination == destination) {
                        selected.warning?.let(model::message)
                        delivered = model.cardCapture.captured(destination, selected.photo.original, selected.photo.displayFile,
                            selected.photo.format, print = destination.cardId == null, motion = selected.photo.motion)
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) { model.message(failure.message ?: "照片未能导入，请重试") }
                finally { if (!delivered) shot?.discard(); runCatching(selection.release); importing = false }
            }
        }
    }, model::message)
    val current = request?.takeIf { it.requestId == destination.requestId }
    var retained by remember(destination.requestId) { mutableStateOf<com.kigtts.next.state.CardPrintRequest?>(null) }
    if (current != null) retained = current
    // Navigation retains the outgoing camera: keep its printed paper through that exit, rather than reveal a stopped preview.
    val print = current ?: retained
    val active = LocalPageActive.current
    RegisterCameraPullGuard(print != null || importing || copying)
    PredictiveBackHandler(active && (importing || copying)) { events ->
        try { events.collect { } } catch (_: CancellationException) { }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CardCameraScreen(
            onCapture = { shot -> check(model.cardCapture.captured(destination, shot.original, shot.displayFile,
                shot.format, print = destination.cardId == null, motion = shot.motion)) { "照片正在处理，请稍候重试" } },
            onCancel = model::navigateBack, message = model::message,
            initialFormat = destination.cardId?.let { id -> model.cards.cards.value.firstOrNull { it.id == id } }
                ?.let { model.stickers.frameStyle(it).format }, suspended = print != null || importing || copying,
            onGallery = { format -> galleryFormat = format; picker.gallery() })
        if (print != null) PolaroidPrinter(print, model.stickers::imageFile,
            model.stickers.frameStyle(print.card), active, { model.cardCapture.finishPrint(print.requestId) },
            previewHandoff = { origin -> model.cardCapture.preparePrintPreview(print.requestId, origin) })
    }
}
