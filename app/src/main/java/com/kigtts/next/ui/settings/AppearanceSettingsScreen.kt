@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import com.kigtts.next.theme.*
import com.kigtts.next.theme.ThemeSettings
import com.kigtts.next.ui.components.*

@Composable
fun AppearanceSettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit,
    placeholder: String = "点击输入文字", onPlaceholderChange: (String) -> Unit = {},
    subtitle: com.kigtts.next.state.SubtitleAppearance = com.kigtts.next.state.SubtitleAppearance(),
    onSubtitleChange: (com.kigtts.next.state.SubtitleAppearance) -> Unit = {}) {
    ReadablePage(840.dp) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        BottomSheetScaffold(sheetPeekHeight = (viewport * .53f).coerceIn(180.dp, 420.dp),
          sheetDragHandle = { BottomSheetDefaults.DragHandle() },
          sheetContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          sheetContent = {
          Column(Modifier.fillMaxWidth().heightIn(max = viewport * .92f)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
            .testTag("appearance-settings-sheet"), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AppearanceAssetSettings(settings, onChange)
        ThemeColorSettings(settings, onChange)
        AppearanceDisplaySettings(settings, onChange)
        SubtitlePlaceholderSetting(placeholder, onPlaceholderChange)
        SubtitleMotionSettings(subtitle, onSubtitleChange)
        OutlinedButton(shapes = expressivePillShapes(), onClick = feedbackClick { onChange(ThemeSettings()) },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Restore, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("恢复默认外观")
        }
          }
        }) { insets ->
          Box(Modifier.fillMaxSize().padding(insets).testTag("appearance-preview")) {
            AppearanceBackdrop(settings, Modifier.matchParentSize())
            Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
              Text("外观预览", style = MaterialTheme.typography.labelLarge)
              Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .88f)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text(placeholder.ifBlank { "你好，KIGTTS" }, style = LocalAppearanceFont.current.apply(
                    androidx.compose.ui.text.TextStyle(fontSize = 30.sp)), maxLines = 2)
                  Text("让每一句话，都有你的风格。", style = MaterialTheme.typography.bodyLarge)
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = feedbackClick {}) { Text("快捷文本") }
                    FilledTonalButton(onClick = feedbackClick {}) { Text("拍立得") }
                  }
                }
              }
            }
          }
        }
      }
    }
}
