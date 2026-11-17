@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.*

/** A translucent layer over the unchanged home page; recognition stays owned by its service. */
@Composable
internal fun VoiceInputSurface(state: AppState, model: AppViewModel) {
    val gesture = model.voiceInputGesture
    LaunchedEffect(state.voiceInputActive, state.continuousRecognition, gesture.pendingRelease) {
        model.dispatchVoiceGestureRelease()
    }
    val reducedMotion = state.subtitleAppearance.reducedMotion
    AnimatedVisibility(state.voiceInputActive && !state.continuousRecognition,
        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(160)) + slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it / 12 },
        exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(120)) + slideOutVertically(tween(170, easing = FastOutSlowInEasing)) { it / 12 }) {
        Box(Modifier.fillMaxSize().testTag("voice-input-live")
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = .68f))) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
            val scroll = rememberScrollState()
            LaunchedEffect(state.voiceInputText) { scroll.scrollTo(scroll.maxValue) }
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
              val landscape = maxWidth > maxHeight
              val transcript: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier.padding(horizontal = 28.dp, vertical = 20.dp), contentAlignment = Alignment.BottomStart) {
                    Text(state.voiceInputText.ifBlank { uiText("listening.awaiting") }, Modifier.fillMaxWidth().verticalScroll(scroll).testTag("voice-input-text"),
                        style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
                    if (state.voiceInputSubmitting) Text(com.kigtts.next.i18n.uiText("listening.submitting"),
                        Modifier.align(Alignment.TopStart).testTag("voice-input-submitting"), style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = .84f))
                }
              }
              val releases: @Composable () -> Unit = { VoiceInputReleaseZones(gesture, state.autoPlayback,
                    cancel = { model.endVoiceInput(false) }, send = { model.endVoiceInput(true) },
                    read = { model.endVoiceInput(true, read = true) }, continuous = model::continueVoiceInput,
                    landscape = landscape, extensions = model.registry.actions(com.kigtts.plugin.api.UiSlot.VOICE_INPUT_RELEASE, model.context()),
                    extension = { action -> model.endVoiceInput(false); model.invoke(action.plugin.descriptor.id, action.action.id, mapOf("voiceText" to state.voiceInputText)) }) }
              if (landscape) Row(Modifier.fillMaxSize()) {
                  transcript(Modifier.weight(1f).fillMaxHeight())
                  releases()
              } else Column(Modifier.fillMaxSize()) {
                  transcript(Modifier.weight(1f).fillMaxWidth())
                  releases()
              }
            }
        }
    }
}
