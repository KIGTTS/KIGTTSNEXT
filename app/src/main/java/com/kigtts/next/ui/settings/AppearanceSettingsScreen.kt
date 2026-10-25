@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.kigtts.next.theme.*
import com.kigtts.next.ui.components.*

@Composable
fun AppearanceSettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit,
    placeholder: String = "点击输入文字", onPlaceholderChange: (String) -> Unit = {},
    subtitle: com.kigtts.next.state.SubtitleAppearance = com.kigtts.next.state.SubtitleAppearance(),
    onSubtitleChange: (com.kigtts.next.state.SubtitleAppearance) -> Unit = {}, onFonts: () -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
      AppearanceBackdrop(settings, Modifier.matchParentSize())
      ReadablePage(840.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
          val viewport = maxHeight
          val navigation = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
          BottomSheetScaffold(sheetPeekHeight = (viewport * .53f).coerceIn(180.dp, 420.dp),
            sheetDragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = Color.Transparent,
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f),
            sheetContent = {
              Column(Modifier.fillMaxWidth().heightIn(max = viewport * .92f)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .windowInsetsPadding(navigation).testTag("appearance-settings-sheet"),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AppearanceAssetSettings(settings, onChange, onFonts)
                ThemeColorSettings(settings, onChange)
                AppearanceDisplaySettings(settings, onChange)
                SubtitlePlaceholderSetting(placeholder, onPlaceholderChange)
                SubtitleMotionSettings(subtitle, onSubtitleChange)
                OutlinedButton(shapes = expressivePillShapes(), onClick = feedbackClick { onChange(ThemeSettings()) },
                  modifier = Modifier.fillMaxWidth()) {
                  Icon(Icons.Default.Restore, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("恢复默认外观")
                }
              }
            }) { _ ->
              AppearanceHomePreview(settings, placeholder, subtitle,
                Modifier.fillMaxWidth().height(viewport * .43f).padding(12.dp).testTag("appearance-preview"))
            }
        }
      }
    }
}
