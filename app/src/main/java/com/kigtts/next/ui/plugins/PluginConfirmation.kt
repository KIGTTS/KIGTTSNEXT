package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.plugin.api.PluginOutcome

@Composable internal fun PluginConfirmation(request: PluginOutcome.Confirm, dismiss: () -> Unit, confirm: () -> Unit) {
    MotionAlertDialog(onDismissRequest = dismiss, title = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(request.title, Modifier.weight(1f))
            Icon(request.action.icon.vector(request.action.symbol), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error)
        }
    }, text = { if (request.text.isNotBlank()) Text(request.text) },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick(confirm)) { Text(request.action.label) } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(dismiss)) { Text(request.cancelLabel) } })
}
