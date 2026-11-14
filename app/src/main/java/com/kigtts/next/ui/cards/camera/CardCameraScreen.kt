@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import android.net.Uri
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

private data class CameraBoundRequest(val aspect: Float, val mode: CameraMode, val front: Boolean, val rotation: Int, val retry: Int,
    val processing: CameraProcessing, val motion: Boolean)

/** A normal secondary page. The host owns navigation and durable original/filtered image imports. */
@Composable
fun CardCameraScreen(onCapture: (PolaroidCameraCapture) -> Unit, onCancel: () -> Unit, message: (String) -> Unit,
    initialFormat: PolaroidFormat? = null, suspended: Boolean = false, onGallery: (PolaroidFormat) -> Unit = {},
    onSystemCamera: (PolaroidFormat) -> Unit = {}, onBurst: ((List<PolaroidCameraCapture>) -> Unit)? = null,
    onVideo: ((java.io.File, Boolean) -> Unit)? = null, galleryPreview: Uri? = null, gallerySaving: Boolean = false,
    watermarkStore: (() -> Unit)? = null) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val pageActive = LocalPageActive.current
    val pageSettled = LocalPageSettled.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(pageSettled) { if (pageSettled) entered = true }
    val active = pageActive && entered && !suspended && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    CameraStatusBar(pageActive && !suspended)
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
    val (granted, request) = cameraPermission(pageActive && entered)
    val rotation = rememberCameraDisplayRotation(view)
    // Intermediate animated sizes are not read by the rest of the page on every frame.
    val modern = onVideo != null
    val fullFrame = modern && options.framingAspect == CameraAspect.FULL
    val aspect = if (modern) options.framingAspect.ratio(configuration.screenWidthDp, configuration.screenHeightDp) else format.geometry.photoAspectRatio.toFloat()
    val mode = if (modern) options.mode else CameraMode.PHOTO
    val showExposureWarning = active && mode == CameraMode.PRO && options.exposureWarning && !options.settingsVisible
    SideEffect { session.setExposureWarning(showExposureWarning) }
    val processing = if (mode == CameraMode.PHOTO) options.processing else CameraProcessing.STANDARD
    val motion = options.motion && mode == CameraMode.PHOTO
    val viewport by remember(aspect, rotation) { derivedStateOf { cameraViewportKey(viewSize, aspect.toDouble(), rotation) } }
    val desired = CameraBoundRequest(aspect, mode, front, rotation, retry, processing, motion)
    val locked = session.busy || session.countingDown || session.video.active || session.video.finishing
    val configurationLocked = session.countingDown || session.video.active || session.video.finishing || !modern && session.busy
    var openGalleryWhenReady by remember { mutableStateOf(false) }
    var zoomDialVisible by remember { mutableStateOf(false) }
    var exposureOpen by remember(mode, front) { mutableStateOf(false) }
    LaunchedEffect(openGalleryWhenReady, session.busy, active) {
        if (openGalleryWhenReady && !session.busy && active) {
            openGalleryWhenReady = false
            onGallery(format)
        }
    }
    val displayReady by remember(session, desired) { derivedStateOf {
        session.ready && !session.loading && !morphing && bound?.let {
            it == desired
        } == true
    } }
    LaunchedEffect(options.flash, session.ready, locked, session.manual.automaticExposure) {
        if (!locked) session.setFlash(if (session.manual.automaticExposure) options.flash else CameraFlash.OFF)
    }
    LaunchedEffect(formats, locked) {
        if (!locked && format !in formats) format = formats.first()
    }
    LaunchedEffect(active, granted, viewport, desired, locked, morphing) {
        if (!active || !granted) { bound = null; session.unbind() }
        else if (!locked && !morphing && viewport.layoutReady && viewport.matchesPhotoRatio &&
            (bound != desired || !session.ready)) {
            // Keep the previous live session while the rectangle is moving; bind once at its final crop.
            streamTransition.cover()
            coroutineContext.ensureActive()
            bound = null
            session.bind(view, front, rotation, processing, motion, mode)
            coroutineContext.ensureActive()
            bound = desired
        }
    }
    LaunchedEffect(active, displayReady, bound) {
        if (active && displayReady) streamTransition.reveal()
    }
    DisposableEffect(session) { onDispose { session.close() } }
    LaunchedEffect(session.countingDown) { if (session.countingDown) filtersExpanded = false }
    val evAvailable = mode != CameraMode.SCAN && session.manual.automaticExposure && session.exposure.step > 0f
    LaunchedEffect(evAvailable, active) { if (!evAvailable || !active) exposureOpen = false }
    val interceptBack = pageActive && (session.busy || session.video.active || exposureOpen || filtersExpanded && !session.countingDown)
    val backAction = {
        if (session.video.active) session.video.stop()
        else if (session.busy) message("正在拍摄，请稍候")
        else if (exposureOpen) exposureOpen = false else filtersExpanded = false
    }
    RegisterPageBackGuard(interceptBack, backAction)
    RegisterCameraPullGuard(session.busy || session.video.active || session.video.finishing || options.settingsVisible || filtersExpanded || exposureOpen || morphing)
    PredictiveBackHandler(interceptBack) { events ->
        try { events.collect { }; backAction() } catch (_: CancellationException) { }
    }
    val shutter = rememberCameraCaptureAction(session, mode, displayReady && viewport.matchesPhotoRatio,
        format, filter, options, lut, onCapture, onVideo, message)
    RegisterCameraShutterKeys(active && granted && !options.settingsVisible) { shutter() }
    val leave = {
        if (session.video.active) session.video.stop()
        else if (session.busy) message("正在拍摄，请稍候")
        else if (exposureOpen) exposureOpen = false
        else if (filtersExpanded && !session.countingDown) filtersExpanded = false
        else { session.unbind(); onCancel() }
    }
    val header: @Composable (Boolean) -> Unit = { vertical ->
        CameraCornerToolbar(active, session.busy, granted && !configurationLocked,
            leave, options::showSettings, applyInsets = !granted, vertical = vertical, showSettings = !granted)
    }
    if (!granted) { Box(Modifier.fillMaxSize()) { CameraPermissionPanel(request, pageActive); header(false) }; return }
    // Configuration is available during startup/morphing; only capture/focus require a fresh streamed frame.
    val interactive = active && !configurationLocked
    val preview = rememberCameraPreviewContent(view, session) { modifier, vertical ->
        CameraPaperPreview(view, format, filter, session, { viewSize = it }, modifier,
            displayReady = displayReady, morphChanged = { morphing = it },
            options = options, rotation = rotation, active = active, showGuides = mode != CameraMode.SCAN,
            filterStrength = options.filterStrength, lut = lut,
            verticalChrome = vertical, headerOverlay = true, streamTransition = streamTransition, aspectRatio = aspect,
            fullFrame = fullFrame, photoSideInset = CAMERA_PREVIEW_SIDE_INSET, chrome = { layout ->
            CameraOverlayControls(format, formats, filter, interactive, vertical, filtersExpanded,
                { if (modern) options.chooseAspect(CameraAspect.entries[(options.aspect.ordinal + 1) % CameraAspect.entries.size]) else format = nextCameraFormat(formats, format) },
                { exposureOpen = false; options.showSettings() }, { exposureOpen = false; filtersExpanded = !filtersExpanded }, { filter = it },
                options.flash, session.flashAvailable && session.manual.automaticExposure, {
                    options.setFlash(CameraFlash.entries[(options.flash.ordinal + 1) % CameraFlash.entries.size])
                }, options.filterStrength, options::setFilterStrength, layout,
                customLuts = customLuts, selectedLut = selectedLut,
                selectLut = { selectedLut = it; filter = PhotoFilter.CUSTOM },
                importLut = { importLut.launch(arrayOf("*/*")) }, showMotion = mode == CameraMode.PHOTO,
                showFilters = mode != CameraMode.SCAN, showFormat = mode != CameraMode.SCAN, motionButton = {
                    CameraMotionButton(options.motion, session.motionAvailable, session.motionReady,
                        options.motion && (session.loading || session.motionAvailable && !session.motionReady),
                        active && granted && !locked && mode == CameraMode.PHOTO, options::setMotion)
                }, effects = options.effects, changeEffects = options::setEffects, aspectLabel = options.aspect.label.takeIf { modern },
                reserveBack = true)
            if (!exposureOpen) CameraZoomRail(session.zoomRatio, session.minZoomRatio, session.maxZoomRatio,
                active && displayReady && (!session.busy || modern), session::zoomTo, vertical,
                Modifier.matchParentSize(), bounds = layout.zoom, dialVisibilityChanged = { zoomDialVisible = it })
            if (evAvailable && !zoomDialVisible) CameraExposureRail(session.exposure, layout.zoom, vertical,
                cameraZoomRange(session.minZoomRatio, session.maxZoomRatio)?.let(::cameraZoomStops)?.size ?: 0,
                exposureOpen, displayReady && interactive && !session.busy, {
                    filtersExpanded = false; exposureOpen = !exposureOpen
                }, session::setGlobalExposure)
            }) {
            if (mode == CameraMode.PRO && displayReady) session.histogram?.let {
                CameraHistogramOverlay(it, Modifier.align(Alignment.TopStart).padding(
                    start = if (vertical) 60.dp else 10.dp, top = 10.dp))
            }
            if (!modern) CameraManualBadge(session.manual, Modifier.align(Alignment.BottomStart).padding(start = if (vertical) 60.dp else 8.dp,
                bottom = if (vertical) 8.dp else 56.dp))
            CameraBurstStatus(session.burst, Modifier.align(Alignment.Center))
            if (mode == CameraMode.SCAN) CameraScanOverlay(session.scanned, session::clearScan)
        }
    }
    val error: @Composable () -> Unit = { CameraError(session, active) { retry++ } }
    val controls = rememberCameraControlsContent { vertical ->
        val bar: @Composable () -> Unit = { CameraActionBar(session.busy, active, displayReady && viewport.matchesPhotoRatio, session.canFlip,
            leave,
            shutter,
            { front = !front }, vertical, locked,
            countingDown = session.countingDown, cancelCountdown = { session.cancelCountdown() }, showCancel = false,
            gallery = { if (modern && session.busy) { openGalleryWhenReady = true; session.burst.stop() } else onGallery(format) },
            galleryPreview = galleryPreview, configurationLocked = configurationLocked, gallerySaving = gallerySaving,
            systemCamera = if (modern) null else ({ onSystemCamera(format) }),
            burstStart = onBurst?.takeIf { mode == CameraMode.PHOTO }?.let { complete -> { if (displayReady && !locked) {
                val output = CameraPhotoOutput(options.watermark, options.encoding)
                session.startBurst(format, filter, { shots -> complete(if (modern) shots.map { it.withOutput(output) } else shots) },
                    options.filterStrength, lut, options.effects)
            } } },
            burstEnd = session.burst::stop, burstRunning = session.burst.running,
            burstHeld = session.burst.held, burstCount = session.burst.count,
            queueShutter = modern && mode.takesPhotos && options.timerSeconds == 0 && !session.motionAvailable,
            shutter = when {
                mode.recordsVideo -> ({ VideoShutter(session.video.active, session.video.finishing,
                    active && !session.video.finishing && (displayReady || session.video.active), shutter) })
                mode == CameraMode.SCAN -> ({ ScanShutterIndicator() })
                else -> null
            })
        }
        if (modern) CameraModeControls(mode, { filtersExpanded = false; options.chooseMode(it) }, interactive,
            vertical, session.video.active, session.video.durationMs, parameters = {
                if (mode == CameraMode.PRO) CameraProControls(session.manual, session.exposure, displayReady && !locked,
                    vertical, exposureOpen, { filtersExpanded = false; exposureOpen = !exposureOpen },
                    { session.setGlobalExposure(0); exposureOpen = false })
            }, controls = bar) else bar()
    }
    CameraPageLayout(preview, controls, error, header, aspect, zoomDialVisible, fullFrame)
    CameraSettingsPanel(options, flashAvailable = session.flashAvailable && session.manual.automaticExposure, enabled = interactive,
        availableProcessing = if (options.motion) setOf(CameraProcessing.STANDARD) else session.availableProcessing,
        manual = session.manual.takeUnless { modern }, watermarkPreview = { cameraPreviewSnapshot(view) }, watermarkStore = watermarkStore,
        exposureWarningAvailable = session.previewFilterAvailable || !displayReady)
}
