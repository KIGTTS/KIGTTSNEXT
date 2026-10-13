@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.camera.view.PreviewView
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

private data class CameraBoundRequest(val format: PolaroidFormat, val front: Boolean, val rotation: Int, val retry: Int)

/** A normal secondary page. The host owns navigation and durable original/filtered image imports. */
@Composable
fun CardCameraScreen(onCapture: (PolaroidCameraCapture) -> Unit, onCancel: () -> Unit, message: (String) -> Unit,
    initialFormat: PolaroidFormat? = null) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val pageActive = LocalPageActive.current
    val active = pageActive && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val configuration = LocalConfiguration.current
    val formats = cameraFormats(configuration.screenWidthDp, configuration.screenHeightDp, configuration.smallestScreenWidthDp)
    var format by rememberSaveable { mutableStateOf(initialFormat?.takeIf { it in formats } ?: formats.first()) }
    var filter by rememberSaveable { mutableStateOf(PhotoFilter.ORIGINAL) }
    var front by rememberSaveable { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var retry by remember { mutableIntStateOf(0) }
    var morphing by remember { mutableStateOf(false) }
    var bound by remember { mutableStateOf<CameraBoundRequest?>(null) }
    val scope = rememberCoroutineScope()
    val latestMessage = rememberUpdatedState(message)
    val session = remember(context, lifecycleOwner) { PolaroidCameraSession(context, lifecycleOwner, scope) { latestMessage.value(it) } }
    val view = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val (granted, request) = cameraPermission(pageActive)
    val rotation = rememberCameraDisplayRotation(view)
    // Intermediate animated sizes are not read by the rest of the page on every frame.
    val viewport by remember(format, rotation) { derivedStateOf { cameraViewportKey(viewSize, format.geometry.photoAspectRatio, rotation) } }
    val desired = CameraBoundRequest(format, front, rotation, retry)
    val displayReady by remember(session, rotation) { derivedStateOf {
        session.ready && !session.loading && !morphing && bound?.let {
            it.format == format && it.front == front && it.rotation == rotation && it.retry == retry
        } == true
    } }
    LaunchedEffect(formats, session.busy) {
        if (!session.busy && format !in formats) { morphing = true; format = formats.first() }
    }
    LaunchedEffect(active, granted, viewport, desired, session.busy, morphing) {
        if (!active || !granted) { bound = null; session.unbind() }
        else if (!session.busy && !morphing && viewport.layoutReady && viewport.matchesPhotoRatio &&
            (bound != desired || !session.ready)) {
            // Keep the previous live session while the rectangle is moving; bind once at its final crop.
            bound = null
            session.bind(view, front, rotation)
            coroutineContext.ensureActive()
            bound = desired
        }
    }
    DisposableEffect(session) { onDispose { session.unbind() } }
    RegisterPageLoading(session.loading || session.busy || morphing)
    RegisterPageBackGuard(pageActive && session.busy) { message("照片正在保存，请稍候") }
    PredictiveBackHandler(pageActive && session.busy) { events ->
        try { events.collect { }; message("照片正在保存，请稍候") } catch (_: CancellationException) { }
    }
    if (!granted) { CameraPermissionPanel(request, pageActive); return }
    val interactive = active && !session.busy
    val preview = remember(view, session) { movableContentOf<Modifier> { modifier ->
        CameraPaperPreview(view, format, filter, session, { viewSize = it }, modifier,
            displayReady = displayReady, morphChanged = { morphing = it })
    } }
    val error: @Composable () -> Unit = { CameraError(session, active) { retry++ } }
    val filters: @Composable () -> Unit = { CameraFilters(filter, interactive, session.busy) { filter = it } }
    val controls: @Composable (Boolean) -> Unit = { vertical ->
        CameraActionBar(session.busy, active, displayReady && viewport.matchesPhotoRatio, session.canFlip,
            format, formats, { session.unbind(); onCancel() }, { session.capture(format, filter, onCapture) },
            { front = !front }, { morphing = true; format = nextCameraFormat(formats, format) }, vertical, session.zoomRatio)
    }
    CameraPageLayout(preview, filters, controls, error)
}

@Composable
internal fun CameraPageLayout(preview: @Composable (Modifier) -> Unit, filters: @Composable () -> Unit,
    controls: @Composable (Boolean) -> Unit, error: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 12.dp).testTag("polaroid-camera")) {
        if (cameraSideControls(maxWidth.value, maxHeight.value)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    preview(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp))
                    filters(); error()
                }
                Column(Modifier.width(96.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { controls(true) }
            }
        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            preview(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp))
            filters(); error(); controls(false)
        }
    }
}
