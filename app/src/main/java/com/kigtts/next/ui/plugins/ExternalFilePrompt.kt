package com.kigtts.next.ui.plugins

import com.kigtts.next.i18n.uiText

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.*

@Composable
internal fun ExternalFilePrompt(model: AppViewModel) {
    val coordinator = model.externalFiles
    val prompt by coordinator.prompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(coordinator::voicePicked) }
    val enginePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(coordinator::dependencyPicked) }
    LaunchedEffect(coordinator) {
        coordinator.chooseVoiceRequests.collect { voicePicker.launch(arrayOf("*/*")) }
    }
    val current = prompt ?: return
    current.dependency?.let { candidate ->
        LocalPackageInstaller(model, candidate, coordinator::finishDependencyInstaller)
        return
    }
    current.candidate?.let { candidate ->
        LocalPackageInstaller(model, candidate, coordinator::dismiss)
        return
    }
    MotionAlertDialog(onDismissRequest = { if (!current.busy) coordinator.dismiss() },
        title = { Text(when {
            current.busy -> "正在准备"
            current.missing.isNotEmpty() -> "需要语音引擎"
            current.kind.isVoice -> "导入语音包"
            current.kind == ExternalFileType.UNKNOWN -> "无法打开文件"
            else -> "导入备份"
        }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(current.name)
            if (current.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (current.missing.isNotEmpty()) {
                Text(when (current.engineAction) {
                    VoiceImportAction.INSTALL -> if (current.kind == ExternalFileType.PIPER_VOICE)
                        "这个语音包需要 Piper 语音引擎。下载安装包后，在这里选择并安装。"
                    else {
                        val name = if (current.sharedClone) "ZipVoice 或千问语音克隆引擎" else current.kind.engineName
                        "这个语音包需要${name}。请从发布者处获取安装包，再在这里选择并安装。"
                    }
                    VoiceImportAction.ENABLE -> "请先启用${current.kind.engineName}，再导入这个语音包。"
                    null -> "未能加载${current.missing.joinToString("、")}，请在插件管理中检查。"
                })
                if (current.engineAction == VoiceImportAction.INSTALL && current.kind == ExternalFileType.PIPER_VOICE) {
                    TextButton(onClick = dialogFeedbackClick {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PIPER_DOWNLOAD))) }
                            .onFailure { model.message("无法打开下载链接") }
                    }) { Text(uiText("plugin.piper_download")) }
                }
            }
            current.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = {
            if (!current.busy && current.kind != ExternalFileType.UNKNOWN && (current.missing.isEmpty() || current.engineAction != null)) {
                val label = when (current.engineAction) {
                    VoiceImportAction.INSTALL -> "选择安装包"
                    VoiceImportAction.ENABLE -> "启用引擎"
                    null -> if (current.error != null) "重试" else if (current.kind.isVoice) "导入" else "继续"
                }
                FilledTonalButton(onClick = dialogFeedbackClick {
                    when (current.engineAction) {
                        VoiceImportAction.INSTALL -> enginePicker.launch(arrayOf("*/*"))
                        VoiceImportAction.ENABLE -> coordinator.enableEngine()
                        null -> coordinator.confirm()
                    }
                }) { Text(label) }
            }
        },
        dismissButton = { if (!current.busy) TextButton(onClick = dialogFeedbackClick(coordinator::dismiss)) {
            Text(if (current.kind == ExternalFileType.UNKNOWN) "关闭" else "取消")
        } })
}

private const val PIPER_DOWNLOAD =
    "https://modelscope.cn/datasets/LHTSTUDIO/KIGTTS-Official-Plugins/resolve/master/engine.piper-0.8.2.kigplugin?view=false"
