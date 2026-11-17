package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.navigation.RegisterPageLoading
import com.kigtts.plugin.api.QuickTextReply
import com.kigtts.next.plugins.quicktext.groups
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BackupRestoreScreen(model: AppViewModel) {
    val resolver = LocalContext.current.contentResolver
    val backup = model.quickTextBackup
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var exportPath by rememberSaveable { mutableStateOf<String?>(null) }
    var exportGroups by remember { mutableStateOf(false) }
    val importing by backup.pendingImport.collectAsStateWithLifecycle()
    val filename = uiText("backup.quick_filename")
    val exported = uiText("backup.quick_exported")
    val exportFailed = uiText("backup.export_failed")
    val importFailed = uiText("backup.import_failed")
    val prepareFailed = uiText("backup.prepare_failed")
    RegisterPageLoading(busy)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(QuickTextBackup.MIME)) { uri ->
        val path = exportPath; exportPath = null
        if (path != null) scope.launch {
            busy = true
            try {
                if (uri != null) { backup.saveExport(resolver, uri, path); model.message(exported) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: exportFailed) }
            finally { withContext(Dispatchers.IO) { backup.exportFile(path).delete() }; busy = false }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                backup.offerImport(resolver, uri)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: importFailed) }
            finally { busy = false }
        }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val formats = remember(model.registry, state.pluginRevision) { backupSettingsCards(model.registry, model.context()) }
    SettingsLayout {
        SettingsGroup(uiText("quick.dock"), uiText("backup.quick_resources")) {
            SettingItem(uiText("backup.quick_import"), uiText("backup.quick_import_hint"), Icons.Default.FileDownload, SettingPosition.FIRST,
                Modifier.testTag("backup-import"), enabled = !busy, onClick = { load.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
            SettingItem(uiText("backup.quick_export"), uiText("backup.quick_export_hint"), Icons.Default.FileUpload, SettingPosition.LAST,
                Modifier.testTag("backup-export"), enabled = !busy && exportPath == null, onClick = {
                    exportGroups = true
                })
        }
        LegacyBackupImport(model.legacyImports)
        formats.forEach { card -> SettingsGroup(card.title) {
            val links = card.links
            links.forEachIndexed { index, link ->
                val position = when { links.size == 1 -> SettingPosition.SINGLE; index == 0 -> SettingPosition.FIRST; index == links.lastIndex -> SettingPosition.LAST; else -> SettingPosition.MIDDLE }
                SettingItem(link.title, link.summary, link.icon, position, enabled = !busy, onClick = { model.navigate(link.destination) }, badge = link.badge)
            }
        } }
    }
    if (exportGroups) QuickBackupGroupPicker(uiText("backup.pick_export"), model.quickTexts.groups(), uiText("backup.export"), { exportGroups = false }) { selected ->
        exportGroups = false; busy = true
        scope.launch {
            try {
                val file = backup.prepareExport(groupIds = selected.toList())
                exportPath = "exports/${file.name}"; save.launch(filename)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: exportFailed) }
            finally { busy = false }
        }
    }
    importing?.let { selection -> QuickBackupGroupPicker(uiText("backup.pick_restore"), selection.groups, uiText("backup.continue"), {
        backup.discardImport(selection); backup.clearPending()
    }) { selected ->
        backup.clearPending(); busy = true
        scope.launch {
            try {
                when (val reply = backup.importSelected(selection, selected)) {
                    is QuickTextReply.Prepared -> model.navigate(Destination.QuickTextReview(reply.value.token))
                    is QuickTextReply.Failure -> model.message(reply.message)
                    else -> model.message(prepareFailed)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: importFailed) }
            finally { busy = false }
        }
    } }
}
