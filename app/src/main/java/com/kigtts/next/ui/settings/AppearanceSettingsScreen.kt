@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.rememberUiCopy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.kigtts.next.theme.*
import com.kigtts.next.i18n.uiText
import com.kigtts.next.ui.components.*
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit,
    placeholder: String? = null, onPlaceholderChange: (String) -> Unit = {},
    subtitle: com.kigtts.next.state.SubtitleAppearance = com.kigtts.next.state.SubtitleAppearance(),
    onSubtitleChange: (com.kigtts.next.state.SubtitleAppearance) -> Unit = {}, onFonts: () -> Unit = {},
    languageName: String = "", onLanguage: (() -> Unit)? = null) {
    val copy = rememberUiCopy()
    val displayedPlaceholder = placeholder ?: copy("subtitle.placeholder")
    Box(Modifier.fillMaxSize()) {
      ReadablePage(840.dp) {
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
          val viewport = maxHeight
          val navigation = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
          val scope = rememberCoroutineScope()
          val density = LocalDensity.current
          var expanded by rememberSaveable { mutableStateOf(false) }
          val range = rememberUpdatedState(with(density) { (viewport - (viewport * .53f).coerceIn(180.dp, 420.dp).coerceAtMost(viewport)).toPx() })
          val sheet = remember(scope, density.density) { AppearanceSheetMotion(scope, range, density.density, expanded) { expanded = it } }
          LaunchedEffect(SettingFocus.target) { if (SettingFocus.target != null) sheet.expand() }
          AppearanceHomePreview(settings, displayedPlaceholder, subtitle,
            Modifier.fillMaxWidth().height(viewport * .43f).padding(12.dp).testTag("appearance-preview"))
          Surface(Modifier.fillMaxWidth().height(viewport).offset { IntOffset(0, sheet.offset.roundToInt()) }
            .nestedScroll(sheet).interruptAppearanceSheet(sheet),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f)) {
            Column(Modifier.fillMaxSize()) {
              Box(Modifier.fillMaxWidth().height(48.dp).testTag("appearance-sheet-handle")
                .draggable(rememberDraggableState { sheet.drag(it) }, Orientation.Vertical, startDragImmediately = sheet.animating,
                  onDragStarted = { sheet.interrupt() }, onDragStopped = { sheet.settle(it) })
                .clickable(onClick = feedbackClick { sheet.toggle() }).semantics {
                    contentDescription = copy("appearance.resize_sheet")
                    stateDescription = if (sheet.fraction < .5f) copy("camera.expanded") else copy("camera.collapsed")
                    expand { sheet.expand(); true }; collapse { if (sheet.fraction < .5f) sheet.toggle(); true }
                }, contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
              Column(Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .windowInsetsPadding(navigation).testTag("appearance-settings-sheet"),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AppearanceAssetSettings(settings, onChange, onFonts)
                ThemeColorSettings(settings, onChange)
                AppearanceDisplaySettings(settings, onChange)
                SubtitlePlaceholderSetting(displayedPlaceholder, onPlaceholderChange)
                SubtitleMotionSettings(subtitle, onSubtitleChange)
                if (onLanguage != null) SettingsGroup(uiText("language.screen")) {
                    SettingItem(uiText("settings.category.language"), languageName, Icons.Default.Language,
                        SettingPosition.SINGLE, Modifier.testTag("appearance-ui-language"), onClick = onLanguage)
                }
                OutlinedButton(shapes = expressivePillShapes(), onClick = feedbackClick { onChange(ThemeSettings()) },
                  modifier = Modifier.fillMaxWidth()) {
                  Icon(Icons.Default.Restore, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(uiText("appearance.reset"))
                }
              }
            }
          }
        }
      }
    }
}
