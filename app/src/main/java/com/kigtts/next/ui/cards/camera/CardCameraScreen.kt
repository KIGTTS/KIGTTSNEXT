@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import androidx.camera.view.PreviewView
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private data class CameraBoundRequest(val format: PolaroidFormat, val front: Boolean, val rotation: Int, val retry: Int,
    val processing: CameraProcessing, val motion: Boolean)

/** A normal secondary page. The host owns navigation and durable original/filtered image imports. */
@Composable
fun CardCameraScreen(onCapture: (PolaroidCameraCapture) -> Unit, onCancel: () -> Unit, message: (String) -> Unit,
    initialFormat: PolaroidFormat? = null, suspended: Boolean = false, onGallery: (PolaroidFormat) -> Unit = {},
    onSystemCamera: (PolaroidFormat) -> Unit = {}, onBurst: ((List<PolaroidCameraCapture>) -> Unit)? = null) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val pageActive = LocalPageActive.current
    val active = pageActive && !suspended && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    val configuration = LocalConfiguration.current
    val formats = cameraFormats(configuration.screenWidthDp, configuration.screenHeightDp, configuration.smallestScreenWidthDp)
    var format by rememberSaveable { mutableStateOf(initialFormat?.takeIf { it in formats } ?: formats.first()) }
    var filter by rememberSaveable { mutableStateOf(PhotoFilter.ORIGINAL) }
    var front by rememberSaveable { mutableStateOf(false) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val options = rememberCameraOptions()
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var retry by remember { mutableIntStateOf(0) }
    var morphing by remember { mutableStateOf(false) }
    var bound by remember { mutableStateOf<CameraBoundRequest?>(null) }
    val scope = rememberCoroutineScope()
    val lutLibrary = remember(context) { CameraLutLibrary(context) }
    var customLuts by remember { mutableStateOf(emptyList<CameraLutChoice>()) }
    var selectedLut by rememberSaveable { mutableStateOf<String?>(null) }
    val lut = remember(filter, selectedLut, customLuts) { PhotoFilters.lut(filter, customLuts.firstOrNull { it.id == selectedLut }?.lut) }
    LaunchedEffect(lutLibrary) { customLuts = withContext(Dispatchers.IO) { lutLibrary.choices() } }
    val importLut = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch {
        try {
            val choice = withContext(Dispatchers.IO) { lutLibrary.import(uri) }
            customLuts = customLuts.filterNot { it.id == choice.id } + choice
            selectedLut = choice.id; filter = PhotoFilter.CUSTOM
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { message(failure.message ?: "LUT 导入失败") }
    } }
    val latestMessage = rememberUpdatedState(message)
    val session = remember(context, lifecycleOwner) { PolaroidCameraSession(context, lifecycleOwner, scope) { latestMessage.value(it) } }
    val view = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val streamTransition = remember(view) { CameraStreamTransition(view) }
    DisposableEffect(streamTransition) { onDispose { streamTransition.close() } }
    val (granted, request) = cameraPermission(pageActive)
    val rotation = rememberCameraDisplayRotation(view)
    // Intermediate animated sizes are not read by the rest of the page on every frame.
    val viewport by remember(format, rotation) { derivedStateOf { cameraViewportKey(viewSize, format.geometry.photoAspectRatio, rotation) } }
    val desired = CameraBoundRequest(format, front, rotation, retry, options.processing, options.motion)
    val locked = session.busy || session.countingDown
    val displayReady by remember(session, rotation) { derivedStateOf {
        session.ready && !session.loading && !morphing && bound?.let {
            it.format == format && it.front == front && it.rotation == rotation && it.retry == retry && it.processing == options.processing && it.motion == options.motion
        } == true
    } }
    LaunchedEffect(options.flash, session.ready, locked) { if (!locked) session.setFlash(options.flash) }
    LaunchedEffect(formats, locked) {
        if (!locked && format !in formats) { morphing = true; format = formats.first() }
    }
    LaunchedEffect(active, granted, viewport, desired, locked, morphing) {
        if (!active || !granted) { bound = null; session.unbind() }
        else if (!locked && !morphing && viewport.layoutReady && viewport.matchesPhotoRatio &&
            (bound != desired || !session.ready)) {
            // Keep the previous live session while the rectangle is moving; bind once at its final crop.
            streamTransition.cover()
            coroutineContext.ensureActive()
            bound = null
            session.bind(view, front, rotation, options.processing, options.motion)
            coroutineContext.ensureActive()
            bound = desired
        }
    }
    LaunchedEffect(active, displayReady, bound) {
        if (active && displayReady) streamTransition.reveal()
    }
    DisposableEffect(session) { onDispose { session.unbind() } }
    LaunchedEffect(session.countingDown) { if (session.countingDown) filtersExpanded = false }
    RegisterPageLoading(session.loading || session.busy || morphing)
    val interceptBack = pageActive && (session.busy || filtersExpanded && !session.countingDown)
    val backAction = {
        if (session.busy) message("照片正在保存，请稍候") else filtersExpanded = false
    }
    RegisterPageBackGuard(interceptBack, backAction)
    RegisterCameraPullGuard(session.busy || options.settingsVisible || filtersExpanded || morphing)
    PredictiveBackHandler(interceptBack) { events ->
        try { events.collect { }; backAction() } catch (_: CancellationException) { }
    }
    RegisterCameraShutterKeys(active && granted && !options.settingsVisible) {
        if (session.countingDown) session.cancelCountdown()
        else if (!locked && displayReady && viewport.matchesPhotoRatio)
            session.capture(format, filter, onCapture, delaySeconds = options.timerSeconds, strength = options.filterStrength, lut = lut, effects = options.effects)
    }
    val leave = {
        if (session.busy) message("照片正在保存，请稍候")
        else if (filtersExpanded && !session.countingDown) filtersExpanded = false
        else { session.unbind(); onCancel() }
    }
    val header: @Composable () -> Unit = {
        CameraCornerToolbar(active, session.busy, granted && !locked, session.loading || session.busy || morphing,
            leave, options::showSettings, applyInsets = !granted)
    }
    if (!granted) { Box(Modifier.fillMaxSize()) { CameraPermissionPanel(request, pageActive); header() }; return }
    // Configuration is available during startup/morphing; only capture/focus require a fresh streamed frame.
    val interactive = active && !locked
    val preview = rememberCameraPreviewContent(view, session) { modifier, vertical ->
        CameraPaperPreview(view, format, filter, session, { viewSize = it }, modifier,
            displayReady = displayReady, morphChanged = { morphing = it },
            options = options, rotation = rotation, active = active, filterStrength = options.filterStrength, lut = lut,
            verticalChrome = vertical, headerOverlay = true, streamTransition = streamTransition, chrome = { layout ->
            CameraOverlayControls(format, formats, filter, interactive, vertical, filtersExpanded,
                { morphing = true; format = nextCameraFormat(formats, format) },
                options::showSettings, { filtersExpanded = !filtersExpanded }, { filter = it },
                options.flash, session.flashAvailable, {
                    options.setFlash(CameraFlash.entries[(options.flash.ordinal + 1) % CameraFlash.entries.size])
                }, options.filterStrength, options::setFilterStrength, layout, showSettings = false,
                customLuts = customLuts, selectedLut = selectedLut,
                selectLut = { selectedLut = it; filter = PhotoFilter.CUSTOM },
                importLut = { importLut.launch(arrayOf("*/*")) }, motionButton = {
                    CameraMotionButton(options.motion, session.motionAvailable, session.motionReady,
                        options.motion && (session.loading || session.motionAvailable && !session.motionReady),
                        active && granted && !locked, options::setMotion)
                }, effects = options.effects, changeEffects = options::setEffects)
            CameraZoomRail(session.zoomRatio, session.minZoomRatio, session.maxZoomRatio,
                displayReady && interactive, session::zoomTo, vertical,
                Modifier.align(Alignment.TopStart).cameraChromeBounds(layout.zoom).excludeCameraPull())
            }) {
            CameraManualBadge(session.manual, Modifier.align(Alignment.BottomStart).padding(start = if (vertical) 60.dp else 8.dp,
                bottom = if (vertical) 8.dp else 56.dp))
            CameraBurstStatus(session.burst, Modifier.align(Alignment.Center))
        }
    }
    val error: @Composable () -> Unit = { CameraError(session, active) { retry++ } }
    val controls: @Composable (Boolean) -> Unit = { vertical ->
        CameraActionBar(session.busy, active, displayReady && viewport.matchesPhotoRatio, session.canFlip,
            leave,
            { session.capture(format, filter, onCapture, delaySeconds = options.timerSeconds, strength = options.filterStrength, lut = lut, effects = options.effects) },
            { front = !front }, vertical, locked,
            countingDown = session.countingDown, cancelCountdown = { session.cancelCountdown() }, showCancel = false,
            gallery = { onGallery(format) }, systemCamera = { onSystemCamera(format) },
            burstStart = onBurst?.let { complete -> { if (displayReady && !locked)
                session.startBurst(format, filter, complete, options.filterStrength, lut, options.effects) } },
            burstEnd = session.burst::stop, burstRunning = session.burst.running,
            burstHeld = session.burst.held, burstCount = session.burst.count)
    }
    CameraPageLayout(preview, controls, error, header, format.geometry.photoAspectRatio.toFloat())
    CameraSettingsPanel(options, flashAvailable = session.flashAvailable, enabled = interactive,
        availableProcessing = if (options.motion) setOf(CameraProcessing.STANDARD) else session.availableProcessing, manual = session.manual)
}
