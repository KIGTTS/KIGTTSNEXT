package com.kigtts.next.ui.settings

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
    RegisterPageLoading(busy)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val path = exportPath; exportPath = null
        if (path != null) scope.launch {
            busy = true
            try {
                if (uri != null) { backup.saveExport(resolver, uri, path); model.message("快捷文本已导出") }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: "导出失败") }
            finally { withContext(Dispatchers.IO) { backup.exportFile(path).delete() }; busy = false }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                when (val reply = backup.prepareImport(resolver, uri)) {
                    is QuickTextReply.Prepared -> model.navigate(Destination.QuickTextReview(reply.value.token))
                    is QuickTextReply.Failure -> model.message(reply.message)
                    else -> model.message("无法准备导入")
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { model.message(error.message ?: "导入失败") }
            finally { busy = false }
        }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val formats = remember(model.registry, state.pluginRevision) { backupSettingsCards(model.registry, model.context()) }
    SettingsLayout {
        SettingsGroup("快捷文本", "包含分组、文字和媒体附件；导入前会显示修改预览。") {
            SettingItem("导入快捷文本", "选择 KIGTTS .kigcards 备份", Icons.Default.FileDownload, SettingPosition.FIRST,
                Modifier.testTag("backup-import"), enabled = !busy, onClick = { load.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
            SettingItem("导出快捷文本", "保存完整快捷文本库", Icons.Default.FileUpload, SettingPosition.LAST,
                Modifier.testTag("backup-export"), enabled = !busy && exportPath == null, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val file = backup.prepareExport()
                            exportPath = "exports/${file.name}"
                            save.launch("KIGTTS-快捷文本.kigcards")
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (error: Exception) { model.message(error.message ?: "导出失败") }
                        finally { busy = false }
                    }
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
}
