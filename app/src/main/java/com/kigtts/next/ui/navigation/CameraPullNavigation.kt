package com.kigtts.next.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import com.kigtts.next.state.Destination
import com.kigtts.next.state.SidePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class CameraPullMode { ENTER, EXIT }

internal val PageEntry.cameraRequestId: String?
    get() = ((scene as? PageScene.Screen)?.destination as? Destination.CardCamera)?.requestId
internal val PageEntry.isCardsBrowser: Boolean
    get() = scene == PageScene.Side(SidePage.CARDS) || scene == PageScene.Screen(Destination.Cards)

@Stable
internal class CameraPullState(private val scope: CoroutineScope, private val open: () -> String?,
    private val back: () -> Unit, private val currentCamera: () -> String?) {
    var active by mutableStateOf(false); private set
    var cameraRequestId by mutableStateOf<String?>(null); private set
    var parentIdentity by mutableStateOf<String?>(null); private set
    var visible by mutableFloatStateOf(1f); private set
    private var mode = CameraPullMode.ENTER
    private var extent = 1f
    private var distance = 0f
    private var startVisible = 0f
    private var settling = false
    private var paused = false
    private var generation = 0
    private var settlement: Job? = null
    private val guards = mutableSetOf<State<Boolean>>()
    private val regions = mutableSetOf<CameraPullRegion>()
    var rootCoordinates: LayoutCoordinates? = null
    val blocked get() = guards.any { it.value }
    val acceptingGesture get() = !active || (settling || paused) && currentCamera() == cameraRequestId
    fun addGuard(source: State<Boolean>) { guards += source }
    fun removeGuard(source: State<Boolean>) { guards -= source }
    fun addRegion(region: CameraPullRegion) { regions += region }
    fun removeRegion(region: CameraPullRegion) { regions -= region }
    private fun contains(region: CameraPullRegion, point: Offset): Boolean {
        val root = rootCoordinates?.takeIf { it.isAttached } ?: return false
        val child = region.coordinates?.takeIf { it.isAttached } ?: return false
        return region.active.value && runCatching { root.localBoundingBoxOf(child).contains(point) }.getOrDefault(false)
    }
    fun excludedStart(point: Offset) = regions.any { it.kind == CameraPullRegionKind.CONTROL && contains(it, point) }
    fun nativeViewfinderStart(point: Offset) = !excludedStart(point) && regions.any { it.kind == CameraPullRegionKind.VIEWFINDER && contains(it, point) }
    fun entryStart(point: Offset) = !excludedStart(point) && regions.any { it.kind == CameraPullRegionKind.ENTRY && contains(it, point) }
    /** Freeze a settling page on Down; its old animation must not run away before touch slop. */
    fun pauseSettlement(): Boolean {
        if (!active || !settling || currentCamera() != cameraRequestId) return false
        generation++; settlement?.cancel(); settlement = null
        settling = false; paused = true
        return true
    }
    fun resumeSettlement() {
        if (!active || !paused) return
        if (currentCamera() != cameraRequestId) { reset(); return }
        paused = false; settling = true
        settle(1f)
    }
    fun begin(mode: CameraPullMode, parent: String, camera: String?, extent: Float): Boolean {
        if (!acceptingGesture || blocked || extent <= 0f) return false
        val carried = if (active) visible else null
        generation++; settlement?.cancel(); settlement = null; settling = false; paused = false
        this.mode = mode; this.extent = extent; distance = 0f; parentIdentity = parent
        visible = if (mode == CameraPullMode.ENTER) 0f else carried ?: 1f
        startVisible = visible
        cameraRequestId = if (mode == CameraPullMode.ENTER) open() else camera
        active = cameraRequestId != null
        if (!active) parentIdentity = null
        return active
    }
    fun drag(deltaY: Float) {
        if (!active || settling) return
        distance += deltaY / extent
        visible = (startVisible + distance).coerceIn(0f, 1f)
    }
    fun finish(cancelled: Boolean, velocityY: Float = 0f) {
        if (!active || settling) return
        // An unrelated navigation may complete while the original pointer is still held.
        if (currentCamera() != cameraRequestId) { reset(); return }
        settling = true
        val entered = !cancelled && (visible >= .28f || visible >= .07f && velocityY > 700f)
        val exited = !cancelled && (visible <= .72f || visible <= .93f && velocityY < -700f)
        val returnToParent = if (mode == CameraPullMode.ENTER) !entered else exited
        val target = if (returnToParent) 0f else 1f
        // Keep the retained scene controlled until its endpoint, including a reversed/cancelled pull.
        if (returnToParent) back()
        settle(target)
    }
    private fun settle(target: Float) {
        val owner = generation
        settlement = scope.launch {
            try {
                val animation = Animatable(visible)
                animation.animateTo(target, tween((90 + abs(target - visible) * 180).toInt(), easing = FastOutSlowInEasing)) {
                    visible = value
                }
                visible = target
            } finally { if (owner == generation) reset() }
        }
    }
    private fun reset() { active = false; settling = false; paused = false; cameraRequestId = null; parentIdentity = null; settlement = null }
}

internal val LocalCameraPullState = compositionLocalOf<CameraPullState?> { null }

/** Several descendants may guard independently: a printer must not replace the camera's busy guard. */
@Composable
internal fun RegisterCameraPullGuard(blocked: Boolean) {
    val host = LocalCameraPullState.current ?: return
    val source = rememberUpdatedState(blocked)
    DisposableEffect(host, source) { host.addGuard(source); onDispose { host.removeGuard(source) } }
}

class CameraPagePose internal constructor(private val state: CameraPullState, private val camera: Boolean) {
    val position get() = if (camera) state.visible - 1f else .08f * state.visible
    val allowUnderlayResumeMovement get() = !camera && state.active && state.acceptingGesture && !state.blocked
}
