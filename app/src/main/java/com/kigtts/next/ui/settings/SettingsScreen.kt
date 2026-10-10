package com.kigtts.next.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kigtts.next.state.*

@Composable
fun SettingsScreen(open: (Destination) -> Unit) {
    SettingsLayout {
        SettingsGroup("外观与交互",subtitle="调整显示效果和操作反馈") {
            CategoryEntry(SettingsCategory.APPEARANCE, Icons.Default.Palette, SettingPosition.FIRST, open)
            CategoryEntry(SettingsCategory.FEEDBACK, Icons.Default.Vibration, SettingPosition.LAST, open)
        }
        SettingsGroup("功能与扩展",subtitle="管理实验功能和插件") {
            CategoryEntry(SettingsCategory.EXPERIMENTAL, Icons.Default.Science, SettingPosition.FIRST, open)
            CategoryEntry(SettingsCategory.WEB, Icons.Default.Language, SettingPosition.MIDDLE, open)
            SettingItem("插件管理", "语音引擎与扩展", Icons.Default.Extension, SettingPosition.LAST,
                onClick = { open(Destination.Plugins) }, trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
        }
    }
}

@Composable
private fun CategoryEntry(category: SettingsCategory, icon: ImageVector, position: SettingPosition, open: (Destination) -> Unit) =
    SettingItem(category.title, category.summary, icon, position, onClick = { open(Destination.SettingsDetail(category)) },
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
