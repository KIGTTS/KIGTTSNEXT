package com.kigtts.next.ui.settings

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.QuickSortPreference

@Composable
fun FeedbackSettingsScreen(enabled: Boolean, onChange: (Boolean) -> Unit, quickSort: QuickSortPreference) {
    val quickSorting by quickSort.enabled.collectAsStateWithLifecycle()
    SettingsLayout {
        SettingsGroup("操作反馈",subtitle="跟随系统振动设置") {
            ToggleSetting("按键震动反馈", "操作时轻微震动", Icons.Default.Vibration, enabled, onChange)
        }
        SettingsGroup("快捷文本") {
            ToggleSetting("长按快速排序", "无需进入编辑模式即可拖动快捷文本和分组", Icons.Default.DragIndicator,
                quickSorting == true, quickSort::choose)
        }
    }
}
