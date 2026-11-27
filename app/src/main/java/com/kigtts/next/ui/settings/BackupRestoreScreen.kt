package com.kigtts.next.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.i18n.rememberUiCopy
import com.kigtts.next.plugins.quicktext.groups
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.state.localizedQuickTextPresets
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.ui.navigation.RegisterPageLoading
import com.kigtts.plugin.api.QuickTextReply
import com.kigtts.plugin.api.QuickGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun BackupRestoreScreen(model: AppViewModel) {
    val resolver = LocalContext.current.contentResolver
    val texts = model.quickTextBackup
    val restore = model.backupRestore
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var exportName by rememberSaveable { mutableStateOf<String?>(null) }
    var exportGroups by remember { mutableStateOf<List<QuickGroup>?>(null) }
    var pickingPresets by rememberSaveable { mutableStateOf(false) }
    val importing by texts.pendingImport.collectAsStateWithLifecycle()
    val config by restore.pendingConfig.collectAsStateWithLifecycle()
    val restoring by restore.busy.collectAsStateWithLifecycle()
    val restoringLegacy by model.legacyImports.busy.collectAsStateWithLifecycle()
    val busy = working || restoring || restoringLegacy
    val quickFilename = uiText("backup.quick_filename")
    val configFilename = uiText("backup.config_filename")
    val errorImport = uiText("backup.import_failed")
    val errorExport = uiText("backup.export_failed")
    val exported = uiText("backup.quick_exported")
    val configExported = uiText("backup.config_exported")
    val restored = uiText("backup.restored")
    val presetsRestored = uiText("backup.presets_restored")
    RegisterPageLoading(busy)

    fun work(errorText: String, action: suspend () -> Unit) {
        if (working) return
        working = true
        scope.launch {
            try { action() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { model.message(errorText) }
            finally { working = false }
        }
    }

    fun exportFile(name: String): File = if (name.endsWith(".kigconfig")) restore.configExportFile(name)
        else texts.exportFile("exports/$name")
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val name = exportName
        exportName = null
        if (name != null) work(errorExport) {
            try {
                if (uri != null) {
                    if (name.endsWith(".kigconfig")) {
                        restore.saveConfigExport(uri, name)
                        model.message(configExported)
                    } else {
                        texts.saveExport(resolver, uri, "exports/$name")
                        model.message(exported)
                    }
                }
            } finally { withContext(NonCancellable + Dispatchers.IO) { exportFile(name).delete() } }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work(errorImport) { restore.offer(uri) }
    }
    fun export(filename: String, prepare: suspend () -> File) {
        work(errorExport) {
            val file = prepare()
            exportName = file.name
            try {
                save.launch(filename)
            } catch (error: Exception) {
                exportName = null
                withContext(NonCancellable + Dispatchers.IO) { file.delete() }
                throw error
            }
        }
    }

    val state by model.state.collectAsStateWithLifecycle()
    val formats = remember(model.registry, state.pluginRevision) { backupSettingsCards(model.registry, model.context()) }
    SettingsLayout {
        SettingsGroup(uiText("settings.category.backup_restore"), uiText("backup.quick_resources")) {
            SettingItem(uiText("backup.restore_data"), uiText("backup.restore_data_hint"), Icons.Default.FileDownload,
                SettingPosition.FIRST, Modifier.testTag("backup-import"), enabled = !busy && exportName == null, onClick = {
                    try { load.launch(arrayOf("*/*")) } catch (_: Exception) { model.message(errorImport) }
                })
            SettingItem(uiText("backup.config_export"), uiText("backup.config_export_hint"), Icons.Default.SettingsBackupRestore,
                SettingPosition.MIDDLE, Modifier.testTag("backup-config-export"), enabled = !busy && exportName == null,
                onClick = { export(configFilename, restore::prepareConfigExport) })
            SettingItem(uiText("backup.quick_export"), uiText("backup.quick_export_hint"), Icons.Default.FileUpload,
                SettingPosition.MIDDLE, Modifier.testTag("backup-export"), enabled = !busy && exportName == null,
                onClick = { work(errorExport) {
                    exportGroups = model.quickTexts.groups().also { check(it.isNotEmpty()) }
                } })
            SettingItem(uiText("backup.restore_presets"), uiText("backup.restore_presets_hint"), Icons.Default.SettingsBackupRestore,
                SettingPosition.LAST, Modifier.testTag("backup-restore-presets"), enabled = !busy && exportName == null,
                onClick = { pickingPresets = true })
        }
        formats.forEach { card ->
            SettingsGroup(card.title) {
                card.links.forEachIndexed { index, link ->
                    val position = when {
                        card.links.size == 1 -> SettingPosition.SINGLE
                        index == 0 -> SettingPosition.FIRST
                        index == card.links.lastIndex -> SettingPosition.LAST
                        else -> SettingPosition.MIDDLE
                    }
                    SettingItem(link.title, link.summary, link.icon, position, enabled = !busy,
                        onClick = { model.navigate(link.destination) }, badge = link.badge)
                }
            }
        }
    }
    LegacyRestorePreview(model.legacyImports)
    if (pickingPresets) {
        val copy = rememberUiCopy()
        val groups = remember(copy) { localizedQuickTextPresets { copy(it) }.map { QuickGroup(it.id, it.name, it.icon.name) } }
        QuickBackupGroupPicker(uiText("backup.pick_restore"), groups, uiText("backup.restore_presets"),
            initialSelection = emptySet(), dismiss = { pickingPresets = false }) { selected ->
            pickingPresets = false
            work(errorImport) {
                model.installQuickTextPresets(selected)
                model.message(presetsRestored)
            }
        }
    }
    exportGroups?.let { groups ->
        QuickBackupGroupPicker(uiText("backup.pick_export"), groups, uiText("backup.export"),
            dismiss = { exportGroups = null }) { selected ->
            exportGroups = null
            export(quickFilename) { texts.prepareExport(groupIds = selected.toList()) }
        }
    }
    importing?.let { selection ->
        QuickBackupGroupPicker(uiText("backup.pick_restore"), selection.groups, uiText("backup.continue"), dismiss = {
            texts.clearPending()
            scope.launch(Dispatchers.IO) { texts.discardImport(selection) }
        }) { selected ->
            texts.clearPending()
            work(errorImport) {
                when (val reply = texts.importSelected(selection, selected)) {
                    is QuickTextReply.Prepared -> model.navigate(Destination.QuickTextReview(reply.value.token))
                    else -> model.message(errorImport)
                }
            }
        }
    }
    config?.let {
        val cancel = { scope.launch { if (!busy) restore.discardConfig() }; Unit }
        MotionAlertDialog(
            onDismissRequest = cancel,
            dismissEnabled = !busy,
            title = { Text(uiText("backup.restore_config_title")) },
            text = { Text(uiText("backup.restore_config_body")) },
            confirmButton = {
                TextButton(onClick = { work(errorImport) { restore.confirmConfig(); model.message(restored) } },
                    enabled = !busy, modifier = Modifier.testTag("backup-config-confirm")) {
                    Text(uiText("backup.restore_data"))
                }
            },
            dismissButton = { TextButton(onClick = dialogFeedbackClick(cancel), enabled = !busy) { Text(uiText("action.cancel")) } },
        )
    }
}
