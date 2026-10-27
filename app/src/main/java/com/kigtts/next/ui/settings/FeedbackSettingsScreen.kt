package com.kigtts.next.ui.settings

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.QuickSortPreference
import com.kigtts.next.state.HomeExitPreference

@Composable
fun FeedbackSettingsScreen(enabled: Boolean, onChange: (Boolean) -> Unit, quickSort: QuickSortPreference, homeExit: HomeExitPreference) {
    val quickSorting by quickSort.enabled.collectAsStateWithLifecycle()
    val doubleBack by homeExit.doubleBack.collectAsStateWithLifecycle()
    SettingsLayout {
        SettingsGroup("操作反馈",subtitle="跟随系统振动设置") {
            ToggleSetting("按键震动反馈", "操作时轻微震动", Icons.Default.Vibration, enabled, onChange)
        }
        SettingsGroup("快捷文本") {
            ToggleSetting("长按快速排序", "无需进入编辑模式即可拖动快捷文本和分组", Icons.Default.DragIndicator,
                quickSorting == true, quickSort::choose)
        }
        SettingsGroup("返回操作") {
            ToggleSetting("二次返回退出", "在主界面连续按两次返回键退出", Icons.AutoMirrored.Filled.ArrowBack,
                doubleBack != false, homeExit::choose)
        }
    }
}
