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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class PageRole { FOREGROUND, BACKGROUND, EXITING }

private class PageMotionMemory(initial: Float, var role: PageRole) {
    var animation = Animatable(initial)
    var gesture = 0f
    var parentPreview = 0f
}

/** Retire only after reaching the off-screen endpoint; interrupted/re-entered pages reuse their pose. */
@Composable
fun PageMotion(entry: PageEntry, role: PageRole, interactive: Boolean, progress: Float, edge: Int,
    parentPreview: Float, finished: () -> Unit, content: @Composable () -> Unit) {
    val memory = remember(entry) { PageMotionMemory(if (entry.scene == PageScene.Home) 0f else
        if (role == PageRole.FOREGROUND) entry.direction else -.08f, role) }
    val restoringParent = remember(role) { role == PageRole.FOREGROUND && memory.role == PageRole.BACKGROUND }
    val prediction = remember(role) { if (role == PageRole.EXITING) memory.gesture else 0f }
    val leavingDirection = entry.direction
    val animation = remember(role) {
        Animatable(memory.animation.value * (1f - memory.parentPreview)).also { memory.animation = it }
    }
    val latestFinished by rememberUpdatedState(finished)
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val entrance = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
    LaunchedEffect(role, animation) {
        animation.animateTo(when (role) { PageRole.FOREGROUND -> 0f; PageRole.BACKGROUND -> -.08f; PageRole.EXITING -> leavingDirection },
            if (role == PageRole.FOREGROUND && !restoringParent) entrance else spatial)
        if (role == PageRole.EXITING) latestFinished()
    }
    SideEffect {
        memory.role = role
        memory.gesture = if (role == PageRole.FOREGROUND) progress else 0f
        memory.parentPreview = parentPreview
    }
    val currentRole by rememberUpdatedState(role)
    val currentProgress by rememberUpdatedState(progress)
    val currentPreview by rememberUpdatedState(parentPreview)
    val layer = Modifier.fillMaxSize().graphicsLayer {
        val position = animation.value * (1f - currentPreview)
        val departed = if (currentRole == PageRole.EXITING) abs(position).coerceIn(0f, 1f) else 0f
        val gesture = if (currentRole == PageRole.FOREGROUND) currentProgress else prediction * (1f - departed)
        val gestureDirection = entry.direction
        val underlay = if (currentRole != PageRole.EXITING) (-position / .08f).coerceIn(0f, 1f) else 0f
        translationX = size.width * position + 28.dp.toPx() * gesture * gestureDirection
        scaleX = 1f - .015f * underlay - .06f * gesture
        scaleY = scaleX
        shape = RoundedCornerShape((28f * gesture + 12f * underlay).dp)
        clip = gesture > 0f || underlay > 0f
    }.then(if (interactive) Modifier.semantics { testTag = "page-layer/" + entry.identity }
        else Modifier.clearAndSetSemantics { testTag = "page-layer/" + entry.identity })
        .pointerInput(interactive) {
            if (!interactive) awaitEachGesture {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    Surface(layer, color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalPageActive provides interactive) {
            Box {
                content()
                val shade = MaterialTheme.colorScheme.scrim
                Box(Modifier.matchParentSize().graphicsLayer {
                    alpha = if (currentRole != PageRole.EXITING) .12f * (-animation.value * (1f - currentPreview) / .08f).coerceIn(0f, 1f) else 0f
                }.background(shade))
            }
        }
    }
}
