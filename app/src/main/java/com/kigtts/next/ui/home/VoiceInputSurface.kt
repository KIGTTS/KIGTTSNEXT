@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.home

import androidx.compose.animation.*
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
        if (state.voiceInputActive) gesture.consumeRelease()?.let { target ->
            if (!state.continuousRecognition) when (target) {
                VoiceReleaseTarget.SEND -> model.endVoiceInput(true)
                VoiceReleaseTarget.READ -> model.endVoiceInput(true, read = true)
                VoiceReleaseTarget.CANCEL -> model.endVoiceInput(false)
                VoiceReleaseTarget.CONTINUOUS -> model.continueVoiceInput()
            }
        }
    }
    AnimatedVisibility(state.voiceInputActive && !state.continuousRecognition,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 12 },
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 12 }) {
        Box(Modifier.fillMaxSize().testTag("voice-input-live")
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = .68f))) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            })
            val scroll = rememberScrollState()
            LaunchedEffect(state.voiceInputText) { scroll.scrollTo(scroll.maxValue) }
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), contentAlignment = Alignment.BottomStart) {
                    Text(state.voiceInputText.ifBlank { "正在聆听…" }, Modifier.fillMaxWidth().verticalScroll(scroll).testTag("voice-input-text"),
                        style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
                }
                VoiceInputReleaseZones(gesture, state.autoPlayback,
                    cancel = { model.endVoiceInput(false) }, send = { model.endVoiceInput(true) },
                    read = { model.endVoiceInput(true, read = true) }, continuous = model::continueVoiceInput)
            }
        }
    }
}
