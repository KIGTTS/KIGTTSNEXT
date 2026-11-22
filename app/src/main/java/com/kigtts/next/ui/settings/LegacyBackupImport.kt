package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
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
internal fun LegacyBackupImport(controller: LegacyImportController) {
    val busy by controller.busy.collectAsStateWithLifecycle()
    val preview by controller.preview.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(controller::prepare) }
    com.kigtts.next.ui.navigation.RegisterPageLoading(busy)
    SettingsGroup(uiText("backup.legacy_title")) {
        SettingItem(uiText("backup.legacy_import"), uiText("backup.legacy_formats"), Icons.Default.FileDownload,
            SettingPosition.SINGLE, enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) })
    }
    preview?.let { value ->
        MotionAlertDialog(onDismissRequest = controller::cancel, dismissEnabled = !busy,
            title = { Text(uiText("backup.legacy_confirm", "format" to value.format)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("backup.legacy_counts", "groups" to value.groups.size.toString(),
                        "entries" to value.groups.sumOf { it.items.size }.toString(), "cards" to value.cards.size.toString()))
                    Text(uiText("backup.legacy_description"))
                    value.groups.forEach { Text(uiText("backup.legacy_group", "title" to it.title, "count" to it.items.size.toString())) }
                    value.warnings.forEach { Text(it) }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = feedbackClick(controller::confirm)) { Text(uiText("common.import")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = dialogFeedbackClick(controller::cancel)) { Text(uiText("action.cancel")) } })
    }
}
