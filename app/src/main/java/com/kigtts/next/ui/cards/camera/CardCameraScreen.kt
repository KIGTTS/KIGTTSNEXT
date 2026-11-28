@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.cards.camera

import android.net.Uri
import com.kigtts.next.camera.expressions.*
import androidx.camera.view.PreviewView
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import com.kigtts.next.ui.media.VisualMediaPicker
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
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kigtts.next.ui.navigation.*
import com.kigtts.plugin.api.PolaroidFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

private data class CameraBoundRequest(val aspect: Float, val mode: CameraMode, val front: Boolean, val rotation: Int, val retry: Int,
    val processing: CameraProcessing, val motion: Boolean, val videoStabilization: Boolean, val analyzeFaces: Boolean)

/** A normal secondary page. The host owns navigation and durable original/filtered image imports. */
@Composable
internal fun CardCameraScreen(visit: String, onCapture: (PolaroidCameraCapture) -> Unit, onCancel: () -> Unit, message: (String) -> Unit,
    initialFormat: PolaroidFormat? = null, suspended: Boolean = false, onGallery: (PolaroidFormat) -> Unit = {},
    onSystemCamera: (PolaroidFormat) -> Unit = {}, onBurst: ((List<PolaroidCameraCapture>) -> Unit)? = null,
    onVideo: ((java.io.File, Boolean) -> Unit)? = null, galleryPreview: Uri? = null, gallerySaving: Boolean = false,
    watermarkStore: (() -> Unit)? = null, expressionStore: (() -> Unit)? = null,
    expressionManagement: () -> Unit = {}) {
    val copy = rememberCameraCopy()
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val pageActive = LocalPageActive.current
    val pageSettled = LocalPageSettled.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(pageSettled) { if (pageSettled) entered = true }
    val active = pageActive && entered && !suspended && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    com.kigtts.next.ui.components.HideStatusBar(pageActive && !suspended)
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
        catch (failure: Exception) { message(failure.message ?: copy.text("camera.lut_failed")) }
    } }
    val latestMessage = rememberUpdatedState(message)
    val session = remember(context, lifecycleOwner) { PolaroidCameraSession(context, lifecycleOwner, scope) { latestMessage.value(it) } }
    val view = remember(context) { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    val (granted, request) = cameraPermission(pageActive && entered)
    val rotation = rememberCameraDisplayRotation(view)
    val streamState by remember(view) { view.previewStreamState.asFlow() }.collectAsState(PreviewView.StreamState.IDLE)
    val streamTransition = rememberCameraStreamTransition(view, visit, bound?.rotation)
    // Intermediate animated sizes are not read by the rest of the page on every frame.
    val modern = onVideo != null
    val fullFrame = modern && options.framingAspect == CameraAspect.FULL
    val aspect = if (modern) options.framingAspect.ratio(configuration.screenWidthDp, configuration.screenHeightDp) else format.geometry.photoAspectRatio.toFloat()
    val mode = if (modern) options.mode else CameraMode.PHOTO
    val floatingShutter = rememberCameraFloatingShutter(visit)
    val library = remember(context) { ExpressionLibrary.get(context) }
    val revision by library.revision.collectAsState()
    val expressionId = options.expressionId.takeIf { modern && mode != CameraMode.SCAN }
    var stickersExpanded by remember(mode, front) { mutableStateOf(false) }
    val analysisSupported = remember { CameraFaceAnalysis.supported }
    val compiled by produceState<CompiledExpression?>(null, library, expressionId, revision) {
        value = if (expressionId == null || !analysisSupported) null else withContext(Dispatchers.IO) {
            runCatching { library.find(expressionId)?.let(CompiledExpression::compile) }.getOrNull()
        }
    }
    val expressionCatalog by produceState(emptyList<ExpressionEffect>(), library, revision, LocalConfiguration.current, active) {
        if (active) value = withContext(Dispatchers.IO) { library.choices() }
    }
    val enabledEffect = compiled?.takeIf { it.effect.id == expressionId }
    SideEffect { session.faceControls(options.faceFocus && mode != CameraMode.SCAN, enabledEffect) }
    var pickedCodes by remember { mutableStateOf<CameraCodeFrame?>(null) }
    var readingCodeImage by remember { mutableStateOf(false) }
    val scanImage = rememberLauncherForActivityResult(VisualMediaPicker()) { uri ->
        if (uri != null && mode == CameraMode.SCAN && !readingCodeImage) {
            readingCodeImage = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { readCameraCodes(context, uri) }
                    if (result == null) latestMessage.value(copy.text("camera.scan_image_empty"))
                    else pickedCodes = result
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { latestMessage.value(copy.text("camera.image_unreadable")) }
                finally { readingCodeImage = false }
            }
        }
        else if (!readingCodeImage) session.scan.setImporting(false)
    }
    SideEffect { session.scan.setImporting(mode == CameraMode.SCAN && (readingCodeImage || pickedCodes != null)) }
    val showExposureWarning = active && mode == CameraMode.PRO && options.exposureWarning && !options.settingsVisible
    SideEffect { session.setExposureWarning(showExposureWarning) }
    val processing = if (mode == CameraMode.PHOTO && expressionId == null) options.processing else CameraProcessing.STANDARD
    val motion = options.motion && mode == CameraMode.PHOTO
    val motionPreparing = options.motion && (session.loading || session.motionAvailable && !session.motionReady)
    val viewport by remember(aspect, rotation) { derivedStateOf { cameraViewportKey(viewSize, aspect.toDouble(), rotation) } }
    val stabilizeVideo = mode.recordsVideo && options.videoStabilization
    // CameraX selects the preview size from the complete use-case group. Keep the
    // analysis stream across toggle changes and pause only the inference itself.
    val analyzeFaces = analysisSupported && mode != CameraMode.SCAN
    val desired = CameraBoundRequest(aspect, mode, front, rotation, retry, processing, motion, stabilizeVideo, analyzeFaces)
    val locked = session.busy || session.countingDown || session.video.active || session.video.finishing
    val configurationLocked = session.countingDown || session.video.active || session.video.finishing || !modern && session.busy
    var openGalleryWhenReady by remember { mutableStateOf(false) }
    var zoomDialVisible by remember { mutableStateOf(false) }
    var exposureOpen by remember(mode, front) { mutableStateOf(false) }
    var proControl by remember(mode, front) { mutableStateOf<ProControl?>(null) }
    LaunchedEffect(openGalleryWhenReady, session.busy, active) {
        if (openGalleryWhenReady && !session.busy && active) {
            openGalleryWhenReady = false
            onGallery(format)
        }
    }
    val displayReady = session.ready && !session.loading && !morphing && bound == desired &&
        viewport.matchesPhotoRatio && streamState == PreviewView.StreamState.STREAMING
    LaunchedEffect(options.flash, session.ready, locked, session.manual.automaticExposure) {
        if (!locked) session.setFlash(if (session.manual.automaticExposure) options.flash else CameraFlash.OFF)
    }
    LaunchedEffect(formats, locked) {
        if (!locked && format !in formats) format = formats.first()
    }
    LaunchedEffect(active, granted, viewport, desired, locked, morphing) {
        if (!active || !granted) { bound = null; session.unbind() }
        else if (!locked && (bound != desired || !session.ready)) {
            // Cover the preview while the rectangle is still moving; bind only at the final crop.
            val oldRotation = bound?.rotation?.takeIf { it == rotation && session.ready && !session.loading &&
                view.previewStreamState.value == PreviewView.StreamState.STREAMING }
            streamTransition.cover(oldRotation)
            coroutineContext.ensureActive()
            if (morphing || !viewport.layoutReady || !viewport.matchesPhotoRatio) return@LaunchedEffect
            bound = null
            session.bind(view, front, rotation, processing, motion, mode, videoStabilization = stabilizeVideo, analyzeFaces = analyzeFaces)
            coroutineContext.ensureActive()
            bound = desired
        }
    }
    LaunchedEffect(active, displayReady, bound) {
        if (active && displayReady) streamTransition.reveal(rotation)
    }
    LaunchedEffect(mode, active, displayReady, pickedCodes) {
        val pending = pickedCodes ?: return@LaunchedEffect
        if (mode != CameraMode.SCAN) {
            pending.bitmap.recycle()
            pickedCodes = null
        } else if (active && (displayReady || session.error != null)) {
            session.scan.pick(pending)
            pickedCodes = null
        }
    }
    DisposableEffect(session) { onDispose { session.close() } }
    LaunchedEffect(session.countingDown) { if (session.countingDown) filtersExpanded = false }
    val evAvailable = mode != CameraMode.SCAN && session.manual.automaticExposure && session.exposure.step > 0f
    LaunchedEffect(evAvailable, active) { if (!evAvailable || !active) exposureOpen = false }
    val interceptBack = pageActive && (session.busy || session.video.active || session.scan.showing || exposureOpen || stickersExpanded || proControl != null || filtersExpanded && !session.countingDown)
    val backAction = {
        if (session.video.active) session.video.stop()
        else if (session.scan.showing) session.scan.clear()
        else if (session.busy) message(copy.text("camera.capture_busy"))
        else if (exposureOpen) exposureOpen = false
        else if (stickersExpanded) stickersExpanded = false
        else if (proControl != null) proControl = null
        else filtersExpanded = false
    }
    RegisterPageBackGuard(interceptBack, backAction)
    RegisterCameraPullGuard(session.busy || session.video.active || session.video.finishing || options.settingsVisible || stickersExpanded ||
        filtersExpanded || exposureOpen || proControl != null || session.scan.showing || morphing)
    PredictiveBackHandler(interceptBack) { events ->
        try { events.collect { }; backAction() } catch (_: CancellationException) { }
    }
    val shutter = rememberCameraCaptureAction(session, mode, displayReady && viewport.matchesPhotoRatio,
        format, filter, options, lut, onCapture, onVideo, message)
    RegisterCameraShutterKeys(active && granted && !options.settingsVisible) { shutter() }
    val leave = {
        if (session.video.active) session.video.stop()
        else if (session.scan.showing) session.scan.clear()
        else if (session.busy) message(copy.text("camera.capture_busy"))
        else if (exposureOpen) exposureOpen = false
        else if (stickersExpanded) stickersExpanded = false
        else if (proControl != null) proControl = null
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
            fullFrame = fullFrame, photoSideInset = CAMERA_PREVIEW_SIDE_INSET, professional = mode == CameraMode.PRO,
            chrome = { layout ->
            CameraOverlayControls(format, formats, filter, interactive, vertical, filtersExpanded,
                { if (modern) options.chooseAspect(CameraAspect.entries[(options.aspect.ordinal + 1) % CameraAspect.entries.size]) else format = nextCameraFormat(formats, format) },
                { exposureOpen = false; proControl = null; stickersExpanded = false; options.showSettings() },
                { exposureOpen = false; proControl = null; stickersExpanded = false; filtersExpanded = !filtersExpanded }, { filter = it },
                options.flash, session.flashAvailable && session.manual.automaticExposure, options::cycleFlash,
                options.filterStrength, options::setFilterStrength, layout,
                customLuts = customLuts, selectedLut = selectedLut,
                selectLut = { selectedLut = it; filter = PhotoFilter.CUSTOM },
                importLut = { importLut.launch(arrayOf("*/*")) }, showMotion = mode == CameraMode.PHOTO,
                showFilters = mode != CameraMode.SCAN, showFormat = mode != CameraMode.SCAN, motionButton = {
                    CameraMotionButton(options.motion, session.motionAvailable, session.motionReady,
                        motionPreparing,
                        active && granted && !locked && mode == CameraMode.PHOTO, options::setMotion)
                }, effects = options.effects, changeEffects = options::setEffects, aspectLabel = options.aspect.takeIf { modern }?.let(copy::label),
                reserveBack = true)
            CameraViewfinderPicker(stickersExpanded, vertical, layout) {
                CameraFaceStickerPicker(expressionCatalog, options, vertical, interactive,
                    close = { stickersExpanded = false },
                    manage = { stickersExpanded = false; expressionManagement() },
                    store = expressionStore?.let { store -> { stickersExpanded = false; store() } })
            }
            CameraParameterRail(session, layout.zoom, vertical,
                active && displayReady && (!session.busy || modern), displayReady && interactive && !session.busy && !filtersExpanded && !stickersExpanded,
                if (exposureOpen) ProControl.EV else proControl, exposureOpen,
                close = { exposureOpen = false; proControl = null }, dialVisibilityChanged = { zoomDialVisible = it },
                exposureEnabled = displayReady && interactive && !session.busy,
                expose = if (mode != CameraMode.PRO && evAvailable) ({
                    filtersExpanded = false; stickersExpanded = false; exposureOpen = true
                }) else null,
                stickersActive = stickersExpanded || enabledEffect != null,
                stickers = if (modern && analysisSupported && mode != CameraMode.SCAN) ({
                    filtersExpanded = false; exposureOpen = false; proControl = null; stickersExpanded = !stickersExpanded
                }) else null)
            layout.pro?.let { bounds ->
                CameraProRail(mode == CameraMode.PRO && !zoomDialVisible && !filtersExpanded && !stickersExpanded,
                    session.manual, session.exposure, displayReady && !locked, vertical, session::setGlobalExposure,
                    Modifier.align(Alignment.TopStart).cameraChromeBounds(bounds), proControl) { proControl = it }
            }
            }) {
            if (mode == CameraMode.PRO && displayReady) session.histogram?.let {
                CameraHistogramOverlay(it, Modifier.align(Alignment.TopStart).padding(
                    start = if (vertical) 60.dp else 10.dp, top = 10.dp))
            }
            if (!modern) CameraManualBadge(session.manual, Modifier.align(Alignment.BottomStart).padding(start = if (vertical) 60.dp else 8.dp,
                bottom = if (vertical) 8.dp else 56.dp))
            CameraBurstStatus(session.burst, Modifier.align(Alignment.Center))
            if (mode == CameraMode.SCAN) CameraScanOverlay(session.scan, message)
        }
    }
    val error: @Composable () -> Unit = { CameraError(session, active) { retry++ } }
    val queueShutter = modern && mode.takesPhotos && options.timerSeconds == 0 && !session.motionAvailable
    val canCapture = displayReady && viewport.matchesPhotoRatio
    val toolsOpen = options.settingsVisible || stickersExpanded || filtersExpanded || exposureOpen || proControl != null
    val controls = rememberCameraControlsContent { vertical ->
        val bar: @Composable () -> Unit = { CameraActionBar(session.busy, active, canCapture, session.canFlip,
            leave,
            shutter,
            { front = !front }, vertical, locked,
            countingDown = session.countingDown, cancelCountdown = { session.cancelCountdown() }, showCancel = false,
            gallery = {
                if (mode == CameraMode.SCAN) {
                    if (!readingCodeImage) {
                        session.scan.clear()
                        session.scan.setImporting(true)
                        scanImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                } else if (modern && session.busy) { openGalleryWhenReady = true; session.burst.stop() } else onGallery(format)
            },
            galleryPreview = galleryPreview, configurationLocked = configurationLocked || mode == CameraMode.SCAN && readingCodeImage,
            gallerySaving = gallerySaving, scanImage = mode == CameraMode.SCAN,
            systemCamera = if (modern) null else ({ onSystemCamera(format) }),
            burstStart = onBurst?.takeIf { mode == CameraMode.PHOTO && !motion }?.let { complete -> { if (displayReady && !locked) {
                val output = CameraPhotoOutput(options.watermark, options.encoding)
                session.startBurst(format, filter, { shots -> complete(if (modern) shots.map { it.withOutput(output) } else shots) },
                    options.filterStrength, lut, options.effects)
            } } },
            burstEnd = session.burst::stop, burstRunning = session.burst.running,
            burstHeld = session.burst.held, burstCount = session.burst.count,
            queueShutter = queueShutter,
            floatingShutter = floatingShutter.takeIf { mode.takesPhotos && !toolsOpen },
            shutter = when {
                mode.recordsVideo -> ({ VideoShutter(session.video.active, session.video.finishing,
                    active && !session.video.finishing && (displayReady || session.video.active), shutter) })
                mode == CameraMode.SCAN -> ({ ScanShutterIndicator() })
                else -> null
            })
        }
        if (modern) CameraModeControls(mode, { filtersExpanded = false; options.chooseMode(it) }, interactive,
            vertical, session.video.active, session.video.durationMs, controls = bar) else bar()
    }
    CameraPageLayout(preview, controls, error, header, aspect, zoomDialVisible, fullFrame,
        captureBaseline = modern, professional = mode == CameraMode.PRO) { vertical ->
        if (mode.takesPhotos && active && !toolsOpen) {
            val enabled = cameraPhotoShutterEnabled(active, session.busy, canCapture, locked, session.countingDown,
                session.burst.running, queueShutter)
            CameraFloatingShutter(floatingShutter, vertical, enabled, session.busy && !queueShutter,
                session.countingDown, capture = { if (session.burst.running) session.burst.stop() else shutter() })
        }
        CameraVolumeCoachMark(options, active && pageSettled && canCapture && mode.takesPhotos && !locked && !toolsOpen &&
            !floatingShutter.interacting,
            vertical, rotation)
    }
    CameraSettingsPanel(options, flashAvailable = session.flashAvailable && session.manual.automaticExposure, enabled = interactive,
        availableProcessing = if (options.motion) setOf(CameraProcessing.STANDARD) else session.availableProcessing,
        manual = session.manual.takeUnless { modern }, watermarkStore = watermarkStore,
        exposureWarningAvailable = session.previewFilterAvailable || !displayReady,
        videoStabilizationAvailable = session.videoStabilizationAvailable,
        faceControlsAvailable = analysisSupported,
        motionStatus = copy.motionStatus(options.motion, session.motionAvailable, session.motionReady, motionPreparing),
        motionChangeEnabled = !locked, filter = filter, openFilters = {
            options.hideSettings()
            exposureOpen = false
            proControl = null
            stickersExpanded = false
            filtersExpanded = true
        })
}
