package com.kigtts.next.ui.cards.camera

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.navigation.RegisterCameraPullGuard
import com.kigtts.next.ui.navigation.RegisterPageBackGuard
import com.kigtts.next.ui.cards.rememberCardPhotoSources
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** New captures save in the background; older replacement requests retain their editor handoff. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun PolaroidCameraRoute(model: AppViewModel, destination: Destination.CardCamera) {
    val copy = rememberCameraCopy()
    if (destination.cardId == null && !destination.systemCamera) {
        // A new camera visit starts with the gallery icon, independent of earlier queued saves.
        val gallery = remember { CameraVisitGalleryPreview() }
        val saving by model.cameraSaves.count.collectAsStateWithLifecycle()
        val save: (List<PolaroidCameraCapture>) -> Unit = { shots ->
            model.cameraSaves.enqueue(shots, onSaved = gallery.capture())
        }
        CardCameraScreen(destination.requestId, onCapture = { save(listOf(it)) },
            onCancel = model::navigateBack, message = model::message,
            onGallery = { model.navigate(Destination.Cards) },
            onBurst = save,
            onVideo = { file, timeLapse -> model.saveCameraVideo(file, timeLapse, gallery.capture()) },
            galleryPreview = gallery.uri, gallerySaving = saving > 0,
            watermarkStore = if (model.registry.plugins.any { it.descriptor.id == com.kigtts.next.plugins.control.STORE_ID && model.registry.available(it) })
                ({ model.openSpeechPluginStore("水印") }) else null)
        return
    }
    val request by model.cardCapture.printRequest.collectAsStateWithLifecycle()
    val copying by model.cardCapture.processing.collectAsStateWithLifecycle()
    val frameRequest by model.cardCapture.frameRequest.collectAsStateWithLifecycle()
    val burstReview by model.cardCapture.burst.review.collectAsStateWithLifecycle()
    val burstSaved by model.cardCapture.burst.savedCount.collectAsStateWithLifecycle()
    val review = burstReview?.takeIf { it.requestId == destination.requestId }
    val frame = frameRequest?.takeIf { it.requestId == destination.requestId }
    var externalStarted by rememberSaveable(destination.requestId) { mutableStateOf(false) }
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
                        delivered = if (selection.fromCamera) model.cardCapture.prepareFraming(destination, selected.photo.original, selected.photo.displayFile, selected.photo.motion, fromCamera = true)
                        else model.cardCapture.captured(destination, selected.photo.original, selected.photo.displayFile,
                            selected.photo.format, print = destination.cardId == null, motion = selected.photo.motion, fromCamera = false)
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) { model.message(failure.message ?: copy.text("camera.import_photo_failed")) }
                finally { if (!delivered) shot?.discard(); runCatching(selection.release); importing = false }
            }
        }
    }, { message -> model.message(message); if (destination.systemCamera) model.navigateBack() },
        onCameraCancelled = { if (destination.systemCamera) model.navigateBack() })
    val current = request?.takeIf { it.requestId == destination.requestId }
    var retained by remember(destination.requestId) { mutableStateOf<com.kigtts.next.state.CardPrintRequest?>(null) }
    if (current != null) retained = current
    // Navigation retains the outgoing camera: keep its printed paper through that exit, rather than reveal a stopped preview.
    val print = current ?: retained
    val active = LocalPageActive.current
    LaunchedEffect(destination.requestId, active) { if (active && destination.systemCamera && !externalStarted) { externalStarted = true; picker.camera() } }
    RegisterCameraPullGuard(print != null || importing || copying || picker.processing || frame != null || review != null)
    RegisterPageBackGuard(review != null) { if (!copying) model.cardCapture.burst.cancel(destination.requestId) }
    PredictiveBackHandler(active && (importing || copying || review != null)) { events ->
        try { events.collect { }; if (!copying && !importing && review != null) model.cardCapture.burst.cancel(destination.requestId) }
        catch (_: CancellationException) { }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (review != null) CameraBurstReviewScreen(review, copying, burstSaved,
            { model.cardCapture.burst.cancel(destination.requestId) },
            { selected -> model.cardCapture.burst.confirm(destination, selected) })
        else if (frame != null) CameraFramingScreen(frame, copying || importing, {
            model.cardCapture.cancelFraming(destination.requestId)
            if (destination.systemCamera) model.navigateBack()
        }) { format, x, y, zoom -> model.cardCapture.confirmFraming(destination, format, x, y, zoom) }
        else if (destination.systemCamera && print == null) Column(Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            ContainedLoadingIndicator(Modifier.size(48.dp))
            Text(if (picker.processing || importing || copying) copy.text("camera.receive_photos") else copy.text("camera.open_system_camera"),
                style = MaterialTheme.typography.bodyMedium)
        }
        else if (!destination.systemCamera)
        CardCameraScreen(
            destination.requestId,
            onCapture = { shot -> check(model.cardCapture.captured(destination, shot.original, shot.displayFile,
                shot.format, print = destination.cardId == null, motion = shot.motion, photoStyle = shot.photoStyle)) { copy.text("camera.photo_processing") } },
            onCancel = model::navigateBack, message = model::message,
            initialFormat = destination.cardId?.let { id -> model.cards.cards.value.firstOrNull { it.id == id } }
                ?.let { model.stickers.frameStyle(it).format }, suspended = print != null || importing || copying,
            onGallery = { format -> galleryFormat = format; picker.gallery() },
            onSystemCamera = { format -> galleryFormat = format; picker.camera() },
            onBurst = if (destination.cardId == null) ({ shots ->
                model.cardCapture.burst.receive(destination, shots); Unit
            }) else null)
        if (print != null && print.cards.size > 1) BatchPolaroidPrinter(print, model.stickers::imageFile,
            model.stickers::frameStyle, active, { model.cardCapture.finishPrint(print.requestId) })
        else if (print != null) PolaroidPrinter(print, model.stickers::imageFile,
            model.stickers.frameStyle(print.card), active, { model.cardCapture.finishPrint(print.requestId) },
            previewHandoff = { origin -> model.cardCapture.preparePrintPreview(print.requestId, origin) })
    }
}
