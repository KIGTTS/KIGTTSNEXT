@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*
import kotlinx.coroutines.*

@Composable internal fun AppearanceAssetSettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(settings)
    var fonts by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun failed(e: Exception) { android.widget.Toast.makeText(context, e.message ?: "导入失败，请重试", android.widget.Toast.LENGTH_SHORT).show() }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let {
        scope.launch { busy = true; try {
            val key = withContext(Dispatchers.IO) { AppearanceAssets(context).importBackground(it) }
            onChange(current.copy(background = key))
        } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { failed(e) } finally { busy = false } }
    } }
    val font = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let {
        scope.launch { busy = true; try {
            val (key, name) = withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(it, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).substringBeforeLast('.') else "自定义字体"
                } ?: "自定义字体"
                AppearanceAssets(context).importFont(it) to name
            }
            onChange(current.copy(font = key, fontName = name))
        } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { failed(e) } finally { busy = false } }
    } }
    SettingsGroup("背景与字体") {
        SettingItem("背景图片", if (settings.background == null) "未设置" else "已设置", Icons.Default.Image, SettingPosition.FIRST,
            onClick = { if (!busy) background.launch("image/*") }, trailing = {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp))
                else if (settings.background != null) IconButton(feedbackClick { onChange(settings.copy(background = null)) }) { Icon(Icons.Default.Close, "移除背景图片") }
            })
        if (settings.background != null) SettingItem("背景遮罩", "", Icons.Default.Opacity, SettingPosition.MIDDLE) {
            Slider(settings.backgroundDim, { onChange(settings.copy(backgroundDim = it)) })
        }
        SettingItem("字体", settings.fontName, Icons.Default.TextFields, SettingPosition.MIDDLE, onClick = { fonts = true })
        SettingItem("导入字体", "TTF、OTF", Icons.Default.FileOpen, SettingPosition.LAST, onClick = { if (!busy) font.launch(arrayOf("*/*")) })
    }
    if (fonts) FontLibraryDialog(settings, onChange, { fonts = false })
}
