@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class PageRole { FOREGROUND, BACKGROUND, EXITING }

private class PageMotionMemory(initial: Float, var role: PageRole) {
    var animation = Animatable(initial)
    var gesture = 0f
    var parentPreview = 0f
    var overridePosition: Float? = null
    var drawerPose: SideDrawerPose? = null
}

/** Retire only after reaching the off-screen endpoint; interrupted/re-entered pages reuse their pose. */
@Composable
fun PageMotion(entry: PageEntry, role: PageRole, interactive: Boolean, progress: Float, edge: Int,
    parentPreview: Float, finished: () -> Unit, vertical: Boolean = false,
    cameraPose: CameraPagePose? = null, drawerPose: SideDrawerPose? = null, content: @Composable () -> Unit) =
    PageMotion(entry, role, interactive, { ReturnProgress(progress, edge) }, { parentPreview },
        finished, vertical, cameraPose, drawerPose, content)

@Composable
internal fun PageMotion(entry: PageEntry, role: PageRole, interactive: Boolean,
    progress: () -> ReturnProgress, parentPreview: () -> Float, finished: () -> Unit, vertical: Boolean = false,
    cameraPose: CameraPagePose? = null, drawerPose: SideDrawerPose? = null, content: @Composable () -> Unit) {
    var mounted by rememberSaveable(entry.identity) { mutableStateOf(false) }
    val memory = remember(entry) { PageMotionMemory(if (mounted) {
        if (role == PageRole.BACKGROUND) if(vertical) .08f else -.08f else 0f
    } else if (entry.scene == PageScene.Home) 0f else
        if (role == PageRole.FOREGROUND) if(vertical) -1f else entry.direction else if(vertical) .08f else -.08f, role) }
    val restoringParent = remember(role) { role == PageRole.FOREGROUND && memory.role == PageRole.BACKGROUND }
    val prediction = remember(role) { if (role == PageRole.EXITING) memory.gesture else 0f }
    val leavingDirection = if (vertical) -1f else entry.direction
    val controlled = cameraPose != null || drawerPose != null
    val animation = remember(role, vertical, controlled) {
        // A settling gesture can finish between render frames. Read its exact final pose,
        // rather than replaying an exit spring from the last frame's partly visible position.
        val seed = memory.drawerPose?.position ?: memory.overridePosition ?: (memory.animation.value * (1f - memory.parentPreview) -
            if(vertical && entry.cameraRequestId != null && role == PageRole.EXITING) memory.gesture else 0f)
        Animatable(seed).also { memory.animation = it }
    }
    val latestFinished by rememberUpdatedState(finished)
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val entrance = remember { tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing) }
    LaunchedEffect(role, animation, controlled) {
        if (controlled) return@LaunchedEffect
        animation.animateTo(when (role) { PageRole.FOREGROUND -> 0f; PageRole.BACKGROUND -> if(vertical) .08f else -.08f; PageRole.EXITING -> leavingDirection },
            if (vertical || role == PageRole.FOREGROUND && !restoringParent) entrance else spatial)
        if (role == PageRole.EXITING) latestFinished()
    }
    SideEffect {
        mounted = true
        memory.role = role
        memory.drawerPose = drawerPose
        if (!controlled) memory.overridePosition = null
    }
    val currentRole by rememberUpdatedState(role)
    val currentProgress by rememberUpdatedState(progress)
    val currentPreview by rememberUpdatedState(parentPreview)
    val currentPose by rememberUpdatedState(cameraPose)
    val currentDrawerPose by rememberUpdatedState(drawerPose)
    fun position(preview: Float) = currentPose?.position ?: currentDrawerPose?.position ?: animation.value * (1f - preview)
    fun underlay(position: Float): Float =
        if (currentRole == PageRole.EXITING) 0f
        else ((if (currentDrawerPose != null) abs(position) else if (vertical) position else -position) / .08f).coerceIn(0f, 1f)
    val settled by remember(animation) { derivedStateOf {
        currentRole == PageRole.FOREGROUND && currentProgress().progress == 0f && currentPreview() == 0f &&
            abs(currentPose?.position ?: currentDrawerPose?.position ?: animation.value) <= .00001f
    } }
    val layer = Modifier.fillMaxSize().graphicsLayer {
        val preview = currentPreview()
        val position = position(preview)
        memory.parentPreview = preview
        if (controlled) memory.overridePosition = position
        val departed = if (currentRole == PageRole.EXITING) abs(position).coerceIn(0f, 1f) else 0f
        val gesture = if (currentRole == PageRole.FOREGROUND) currentProgress().progress
            else if (vertical) 0f else prediction * (1f - departed)
        memory.gesture = gesture
        val gestureDirection = entry.direction
        val underlay = underlay(position)
        translationX = if(vertical) 0f else size.width * position + 28.dp.toPx() * gesture * gestureDirection
        translationY = if(vertical) size.height * (position - if(entry.cameraRequestId != null && !controlled) gesture else 0f) else 0f
        scaleX = if(vertical) 1f else 1f - .015f * underlay - .06f * gesture
        scaleY = scaleX
        clip = !vertical && (gesture > 0f || underlay > 0f)
        shape = if (clip) RoundedCornerShape((28f * gesture + 12f * underlay).dp) else RectangleShape
    }.then(if (interactive) Modifier.semantics { testTag = "page-layer/" + entry.identity }
        else Modifier.clearAndSetSemantics { testTag = "page-layer/" + entry.identity })
        .pointerInput(interactive) {
            if (!interactive) awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val resume = currentPose?.allowUnderlayResumeMovement == true && event.changes.count { it.pressed } == 1
                    event.changes.forEach { change ->
                        // Block descendant presses/releases while allowing the fixed ancestor to
                        // take over a new upward pull from a partially revealed camera's underlay.
                        if (!resume || !change.pressed || !change.previousPressed) change.consume()
                    }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    Surface(layer, color = MaterialTheme.colorScheme.background) {
        val shade = MaterialTheme.colorScheme.scrim
        CompositionLocalProvider(LocalPageActive provides interactive, LocalPageSettled provides (interactive && settled)) {
            Box {
                content()
                Box(Modifier.matchParentSize().graphicsLayer {
                    alpha = .12f * underlay(position(currentPreview()))
                }.background(shade))
            }
        }
    }
}
