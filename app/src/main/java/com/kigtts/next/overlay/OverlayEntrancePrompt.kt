package com.kigtts.next.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.i18n.rememberUiCopy

@Composable internal fun OverlayEntrancePrompt(model: AppViewModel) {
    val context = LocalContext.current
    val copy = rememberUiCopy()
    val hub = remember { OverlayHub.get(context) }
    val requests by hub.requests.collectAsState()
    val entry = requests.firstOrNull() ?: return
    val plugin = model.pluginManager.status.firstOrNull { it.installed.manifest.id == entry.owner }?.installed
    if (plugin == null || !plugin.enabled || plugin.publisherSha256 != entry.publisher) {
        LaunchedEffect(entry.id) { hub.answer(entry.id, false) }; return
    }
    MotionAlertDialog(onDismissRequest = { hub.answer(entry.id, false) },
        icon = { Icon(Icons.Default.AddCircleOutline, null) }, title = { Text(copy("overlay.add.title")) },
        text = { Text(copy("overlay.add.body", "plugin" to plugin.manifest.name, "entry" to entry.title)) },
        confirmButton = { FilledTonalButton(onClick = dialogFeedbackClick { hub.answer(entry.id, true) }) { Text(copy("overlay.add.confirm")) } },
        dismissButton = { TextButton(onClick = dialogFeedbackClick { hub.answer(entry.id, false) }) { Text(copy("overlay.add.cancel")) } })
}
