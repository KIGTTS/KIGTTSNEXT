@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun AppearanceAssetSettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit, onFonts: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var busy by remember { mutableStateOf(false) }
    var crop by rememberSaveable { mutableStateOf<String?>(null) }
    var prompt by rememberSaveable { mutableStateOf(false) }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let {
        scope.launch { busy = true; try {
            crop = withContext(Dispatchers.IO) { AppearanceAssets(context).importBackground(it) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "无法读取这张图片", android.widget.Toast.LENGTH_SHORT).show() }
        finally { busy = false } }
    } }
    SettingsGroup("背景与字体") {
        SettingItem("背景图片", if (settings.background == null) "未设置" else "已设置", Icons.Default.Image, SettingPosition.FIRST,
            onClick = { if (!busy) background.launch("image/*") }, trailing = {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                else if (settings.background != null) IconButton(feedbackClick {
                    onChange(settings.copy(background = null, backgroundSeed = null, backgroundColorEnabled = false))
                }) { Icon(Icons.Default.Close, "移除背景图片") }
            })
        if (settings.background != null) SettingItem("背景遮罩", "", Icons.Default.Opacity, SettingPosition.MIDDLE) {
            Slider(settings.backgroundDim, { onChange(settings.copy(backgroundDim = it)) })
        }
        SettingItem("字体", settings.fontName, Icons.Default.TextFields, SettingPosition.LAST,
            onClick = onFonts, trailing = { Icon(Icons.Default.ChevronRight, null) })
    }
    crop?.let { source -> BackgroundCropScreen(source, { crop = null }) { key, seed ->
        val next = current.copy(background = key, backgroundSeed = seed)
        prompt = next.askBackgroundColor && next.colorSource != ThemeColorSource.BACKGROUND
        onChange(next); crop = null
    } }
    if (prompt) BackgroundThemePrompt { switch, remind ->
        val next = current.copy(askBackgroundColor = remind)
        onChange(if (switch) next.withColorSource(ThemeColorSource.BACKGROUND) else next)
        prompt = false
    }
}
