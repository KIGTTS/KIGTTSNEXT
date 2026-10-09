package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.feedbackChange

@Composable
fun ExperimentalSettingsScreen(appleLivePhotos: Boolean, change: (Boolean) -> Unit) {
    SettingsLayout {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("苹果实况照片", style = MaterialTheme.typography.titleMedium)
                    Text("允许导入照片与视频配对，并播放配对的动态内容。默认关闭。Android 动态照片始终可用。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(appleLivePhotos, feedbackChange(change), Modifier.testTag("apple-live-photos"))
            }
        }
    }
}
