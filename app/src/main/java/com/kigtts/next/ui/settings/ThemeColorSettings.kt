@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.rememberUiCopy

import com.kigtts.next.i18n.uiText
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun ThemeColorSettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    val copy = rememberUiCopy()
    var picker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var selectionRequest by remember { mutableIntStateOf(0) }
    val custom = settings.colorSource == ThemeColorSource.CUSTOM
    SettingsGroup(uiText("appearance.theme_palette")) {
        SettingItem(uiText("appearance.theme_mode"), "", Icons.Default.BrightnessAuto, SettingPosition.FIRST) {
            SettingChoiceGroup(uiText("appearance.theme_mode"), ThemeMode.entries, settings.mode, { copy("appearance.mode." + it.name.lowercase(java.util.Locale.ROOT)) }, { onChange(settings.copy(mode = it)) })
        }
        SettingItem(uiText("appearance.color_source"), "", Icons.Default.Palette, if (custom) SettingPosition.MIDDLE else SettingPosition.LAST,
            modifier = Modifier.testTag("theme-color-source")) {
            ChoiceButtonGroup(uiText("appearance.color_source"), ThemeColorSource.entries, settings.colorSource, { copy("appearance.source." + it.name.lowercase(java.util.Locale.ROOT)) }, { source ->
                val request = ++selectionRequest
                val message = when {
                    source == ThemeColorSource.WALLPAPER && Build.VERSION.SDK_INT < 31 -> copy("appearance.wallpaper_requirement")
                    source == ThemeColorSource.BACKGROUND && settings.background == null -> copy("appearance.background_pick_first")
                    else -> null
                }
                if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                else if (source == ThemeColorSource.BACKGROUND && settings.backgroundSeed == null) {
                    val background = settings.background
                    scope.launch {
                        val seed = withContext(Dispatchers.IO) { AppearanceBackgrounds.load(context, background)?.let(::backgroundThemeSeed) }
                        if (request == selectionRequest && current.background == background) {
                            if (seed != null) onChange(current.copy(backgroundSeed = seed).withColorSource(source))
                            else Toast.makeText(context, copy("appearance.background_read_failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else onChange(settings.withColorSource(source))
            }, compactLabels = true)
        }
        AnimatedVisibility(custom, enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(180)) + shrinkVertically(tween(180))) {
            SettingItem(uiText("appearance.custom_seed"), settings.hex, null, SettingPosition.LAST, modifier = Modifier.testTag("custom-theme-color"),
            onClick = { picker = true }, trailing = {
                IconButton(feedbackClick { picker = true }) { Box(Modifier.size(28.dp).background(Color(settings.seed), CircleShape)) }
            })
        }
    }
    if (picker) ColorPickerDialog(settings.seed, onConfirm = { color ->
        selectionRequest++
        onChange(settings.copy(seed = color).withColorSource(ThemeColorSource.CUSTOM)); picker = false
    }, onDismiss = { picker = false }, title = uiText("appearance.choose_color"))
}
