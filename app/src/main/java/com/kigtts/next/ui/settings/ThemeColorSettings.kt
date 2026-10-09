@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*

@Composable
internal fun ThemeColorSettings(settings:ThemeSettings,onChange:(ThemeSettings)->Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    val wallpaperAvailable=Build.VERSION.SDK_INT>=31
    val customColorEnabled=!(settings.wallpaperEnabled&&wallpaperAvailable)
    SettingsGroup("主题与配色") {
        SettingItem("主题模式","",Icons.Default.BrightnessAuto,SettingPosition.FIRST) {
            SettingChoiceGroup("主题模式",ThemeMode.entries,settings.mode,{ it.label },{ onChange(settings.copy(mode=it)) })
        }
        ToggleSetting("壁纸取色",if(wallpaperAvailable)"使用壁纸颜色" else "需要 Android 12 及以上",
            Icons.Default.Wallpaper,settings.wallpaperEnabled&&wallpaperAvailable,{ onChange(settings.copy(wallpaperEnabled=it)) },
            position=SettingPosition.MIDDLE,enabled=wallpaperAvailable)
        SettingItem("主题色",if(customColorEnabled)settings.hex else "由壁纸决定",Icons.Default.Palette,SettingPosition.LAST,
            onClick=if(customColorEnabled)({ picker=true }) else null,trailing={
                FilledTonalIconButton(feedbackClick { picker=true },enabled=customColorEnabled,
                    modifier=Modifier.semantics { contentDescription="选择主题色" }) {
                    Box(Modifier.size(24.dp).background(Color(settings.seed),CircleShape))
                }
            })
    }
    if(picker&&customColorEnabled)ColorPickerDialog(settings.seed,onConfirm={ color ->
        onChange(settings.copy(seed=color));picker=false
    },onDismiss={ picker=false },title="选择主题色")
}
