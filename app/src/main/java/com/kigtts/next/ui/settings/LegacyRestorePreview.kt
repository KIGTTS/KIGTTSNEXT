package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.legacy.LegacyImportController
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.components.feedbackClick

@Composable
internal fun LegacyRestorePreview(controller: LegacyImportController) {
    val busy by controller.busy.collectAsStateWithLifecycle()
    val preview by controller.preview.collectAsStateWithLifecycle()
    preview?.let { value ->
        MotionAlertDialog(onDismissRequest = controller::cancel, dismissEnabled = !busy,
            title = { Text(uiText("backup.legacy_confirm", "format" to uiText("backup.legacy_title"))) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("backup.legacy_counts", "groups" to value.groups.size.toString(),
                        "entries" to value.groups.sumOf { it.items.size }.toString(), "cards" to value.cards.size.toString()))
                    Text(uiText("backup.legacy_description"))
                    value.groups.forEach { Text(uiText("backup.legacy_group", "title" to it.title, "count" to it.items.size.toString())) }
                    if (value.preferences.isNotEmpty()) Text(uiText("backup.legacy_settings_notice",
                        "count" to value.preferences.size.toString()))
                    if (value.components.any { it in setOf("voice_packs", "lock_screen_wallpaper") })
                        Text(uiText("backup.legacy_voice_notice"))
                    value.warnings.forEach { Text(uiText(it)) }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = feedbackClick(controller::confirm)) { Text(uiText("common.import")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = dialogFeedbackClick(controller::cancel)) { Text(uiText("action.cancel")) } })
    }
}
