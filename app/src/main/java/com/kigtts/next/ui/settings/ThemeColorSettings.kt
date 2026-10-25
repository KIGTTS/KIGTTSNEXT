@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import android.os.Build
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun ThemeColorSettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    var picker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var selectionRequest by remember { mutableIntStateOf(0) }
    SettingsGroup("主题与配色") {
        SettingItem("主题模式", "", Icons.Default.BrightnessAuto, SettingPosition.FIRST) {
            SettingChoiceGroup("主题模式", ThemeMode.entries, settings.mode, { it.label }, { onChange(settings.copy(mode = it)) })
        }
        SettingItem("取色", "", Icons.Default.Palette, SettingPosition.MIDDLE) {
            ChoiceButtonGroup("取色", ThemeColorSource.entries, settings.colorSource, { it.label }, { source ->
                val request = ++selectionRequest
                val message = when {
                    source == ThemeColorSource.WALLPAPER && Build.VERSION.SDK_INT < 31 -> "壁纸取色需要 Android 12 及以上"
                    source == ThemeColorSource.BACKGROUND && settings.background == null -> "请先选择背景图片"
                    else -> null
                }
                if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                else if (source == ThemeColorSource.BACKGROUND && settings.backgroundSeed == null) {
                    val background = settings.background
                    scope.launch {
                        val seed = withContext(Dispatchers.IO) { AppearanceBackgrounds.load(context, background)?.let(::backgroundThemeSeed) }
                        if (request == selectionRequest && current.background == background) {
                            if (seed != null) onChange(current.copy(backgroundSeed = seed).withColorSource(source))
                            else Toast.makeText(context, "无法读取背景图片，请重新选择", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else onChange(settings.withColorSource(source))
            }, compactLabels = true)
        }
        if (settings.colorSource == ThemeColorSource.CUSTOM) SettingItem("主题色", settings.hex, null, SettingPosition.LAST,
            onClick = { picker = true }, trailing = {
                IconButton(feedbackClick { picker = true }) { Box(Modifier.size(28.dp).background(Color(settings.seed), CircleShape)) }
            })
    }
    if (picker) ColorPickerDialog(settings.seed, onConfirm = { color ->
        selectionRequest++
        onChange(settings.copy(seed = color).withColorSource(ThemeColorSource.CUSTOM)); picker = false
    }, onDismiss = { picker = false }, title = "选择主题色")
}
