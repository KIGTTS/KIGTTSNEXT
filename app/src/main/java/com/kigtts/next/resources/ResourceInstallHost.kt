package com.kigtts.next.resources

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable fun ResourceInstallHost(model: AppViewModel) {
    val pending by model.resourceInstalls.prompt.collectAsState()
    val prompt = pending ?: return
    val archive = prompt.archive
    val missingVoice = model.resourceInstalls.missingVoice()
    MotionAlertDialog(onDismissRequest = model.resourceInstalls::dismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (missingVoice) "先添加语音服务" else "添加${archive.manifest.resourceType.label}", Modifier.weight(1f))
            com.kigtts.next.ui.components.MaterialSymbols.vector(archive.manifest.resourceType.symbol)?.let { Icon(it, null, Modifier.size(24.dp)) }
        } }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(archive.manifest.name, style = MaterialTheme.typography.titleMedium)
            Text(if (missingVoice) "需要语音合成服务和 Piper 语音引擎。安装后，回到下载管理继续添加语音包。"
                else "${archive.count} 项${archive.manifest.resourceType.label}")
            if (archive.manifest.resourceType == com.kigtts.plugin.api.ResourceType.QUICK_TEXT) Text("添加为新的快捷文本分组。", style = MaterialTheme.typography.bodyMedium)
            prompt.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (prompt.busy) { Text("正在添加…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } }, confirmButton = { Button(dialogFeedbackClick(model.resourceInstalls::confirm), enabled = !prompt.busy) {
            Text(if (missingVoice) "前往商店" else if (prompt.error != null) "重试" else "添加")
        } }, dismissButton = { TextButton(dialogFeedbackClick(model.resourceInstalls::dismiss)) { Text(if (prompt.busy) "后台添加" else "取消") } })
}
