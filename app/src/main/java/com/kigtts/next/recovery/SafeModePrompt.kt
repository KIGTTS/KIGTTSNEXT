package com.kigtts.next.recovery

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
    MotionAlertDialog(onDismissRequest = { if (!state.restoring) enter() }, title = { Text("已进入安全模式") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("KIGTTS 连续多次崩溃，已暂时停用插件。你可以恢复插件、检查插件，或先进入软件。")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.restoring) LinearProgressIndicator(Modifier.fillMaxWidth())
        } }, confirmButton = { Column(Modifier.fillMaxWidth()) {
            FilledTonalButton(feedbackClick { if (!state.restoring) restore() }, Modifier.fillMaxWidth()) { Text("恢复插件") }
            MotionTextButton(dialogFeedbackClick { if (!state.restoring) manage() }, Modifier.fillMaxWidth()) { Text("进入插件管理") }
            MotionTextButton(dialogFeedbackClick { if (!state.restoring) enter() }, Modifier.fillMaxWidth()) { Text("进入软件") }
        } })
}
