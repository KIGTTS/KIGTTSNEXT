package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*

@Composable
internal fun ExternalFilePrompt(model: AppViewModel) {
    val coordinator = model.externalFiles
    val prompt by coordinator.prompt.collectAsStateWithLifecycle()
    val app by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(app.destination) {
        if (app.destination == Destination.Home || app.destination == Destination.SettingsDetail(SettingsCategory.BACKUP_RESTORE)) coordinator.resume()
    }
    val current = prompt ?: return
    current.candidate?.let { candidate ->
        LocalPackageInstaller(model, candidate, coordinator::dismiss)
        return
    }
    MotionAlertDialog(onDismissRequest = { if (!current.busy) coordinator.dismiss() },
        title = { Text(when {
            current.busy -> "正在准备"
            current.missing.isNotEmpty() -> "需要语音插件"
            current.kind == ExternalFileType.PIPER_VOICE -> "导入语音包"
            current.kind == ExternalFileType.UNKNOWN -> "无法打开文件"
            else -> "导入备份"
        }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(current.name)
            if (current.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (current.missing.isNotEmpty()) Text("请先安装或启用${current.missing.joinToString("和")}，再导入这个语音包。")
            current.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { if (!current.busy && current.kind != ExternalFileType.UNKNOWN) FilledTonalButton(
            onClick = dialogFeedbackClick { if (current.missing.isEmpty()) coordinator.confirm() else coordinator.guide() }) {
            Text(if (current.missing.isNotEmpty()) "前往商店" else if (current.error != null) "重试" else "继续")
        } },
        dismissButton = { if (!current.busy) TextButton(onClick = dialogFeedbackClick(coordinator::dismiss)) {
            Text(if (current.kind == ExternalFileType.UNKNOWN) "关闭" else "取消")
        } })
}
