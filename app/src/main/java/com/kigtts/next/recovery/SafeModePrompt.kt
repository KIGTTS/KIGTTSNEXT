package com.kigtts.next.recovery

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.launch

@Composable internal fun SafeModePromptHost(model: AppViewModel) {
    val state by model.safeModeSession.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (state.prompt) SafeModePrompt(state,
        restore = { scope.launch { model.safeModeSession.restorePlugins() } },
        manage = { model.safeModeSession.enterSoftware(); model.navigate(Destination.Plugins) },
        enter = model.safeModeSession::enterSoftware)
}

@Composable internal fun SafeModePrompt(state: SafeModeStatus, restore: () -> Unit, manage: () -> Unit, enter: () -> Unit) {
    MotionAlertDialog(onDismissRequest = { if (!state.restoring) enter() }, title = { Text(uiText("recovery.title")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("recovery.body"))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.restoring) LinearProgressIndicator(Modifier.fillMaxWidth())
        } }, confirmButton = { Column(Modifier.fillMaxWidth()) {
            FilledTonalButton(feedbackClick { if (!state.restoring) restore() }, Modifier.fillMaxWidth()) { Text(uiText("recovery.restore")) }
            MotionTextButton(dialogFeedbackClick { if (!state.restoring) manage() }, Modifier.fillMaxWidth()) { Text(uiText("recovery.manage")) }
            MotionTextButton(dialogFeedbackClick { if (!state.restoring) enter() }, Modifier.fillMaxWidth()) { Text(uiText("recovery.enter")) }
        } })
}
