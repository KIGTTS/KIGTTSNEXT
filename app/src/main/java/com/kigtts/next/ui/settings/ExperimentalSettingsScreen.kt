package com.kigtts.next.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOn

@Composable
fun ExperimentalSettingsScreen(appleLivePhotos: Boolean, change: (Boolean) -> Unit) {
    SettingsLayout {
        SettingsGroup("媒体") {
            ToggleSetting("苹果实况照片", "配对导入照片与视频，并播放动态片段。Android 动态照片无需开启此选项。",
                Icons.Default.MotionPhotosOn, appleLivePhotos, change, modifier = Modifier.testTag("apple-live-photos"))
        }
    }
}
