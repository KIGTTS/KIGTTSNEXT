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
            ToggleSetting("苹果实况照片", "导入照片和配套视频",
                Icons.Default.MotionPhotosOn, appleLivePhotos, change, modifier = Modifier.testTag("apple-live-photos"))
        }
    }
}
