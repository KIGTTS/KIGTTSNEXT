package com.kigtts.next.sharing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.ui.components.*

@Composable internal fun CloudSharingSetupHost(model: AppViewModel) {
    val setup = model.sharing.setup
    val state by setup.state.collectAsStateWithLifecycle()
    val current = state ?: return
    MotionAlertDialog(onDismissRequest = setup::dismiss, dismissEnabled = !current.busy,
        title = { Text(if (current.busy) "正在启用云端互传" else "使用云端互传？") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(current.error ?: "启用后可跨网络分享，并自动安装云共享插件。需要联网，优先设备直连，必要时中转加密数据，不保留文件。")
            if (current.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { MotionTextButton(onClick = dialogFeedbackClick(setup::useCloud), enabled = !current.busy) {
            Text(if (current.error == null) "启用并继续" else "重试")
        } },
        dismissButton = { MotionTextButton(onClick = dialogFeedbackClick(setup::useSystem), enabled = !current.busy) { Text("使用系统分享") } })
}
