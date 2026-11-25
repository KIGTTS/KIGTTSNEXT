package com.kigtts.next.ui.settings

import com.kigtts.next.i18n.uiText

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.QuickSortPreference
import com.kigtts.next.state.HomeExitPreference
import com.kigtts.next.state.MediaQuickTextCollapse
import com.kigtts.next.i18n.rememberUiCopy

@Composable
fun FeedbackSettingsScreen(enabled: Boolean, onChange: (Boolean) -> Unit, quickSort: QuickSortPreference, homeExit: HomeExitPreference,
    mediaCollapse: MediaQuickTextCollapse, onMediaCollapse: (MediaQuickTextCollapse) -> Unit) {
    val copy = rememberUiCopy()
    val quickSorting by quickSort.enabled.collectAsStateWithLifecycle()
    val doubleBack by homeExit.doubleBack.collectAsStateWithLifecycle()
    SettingsLayout {
        SettingsGroup(uiText("settings.category.feedback"),subtitle=uiText("feedback.system")) {
            ToggleSetting(uiText("feedback.haptic"), uiText("feedback.haptic.hint"), Icons.Default.Vibration, enabled, onChange)
        }
        SettingsGroup(uiText("feedback.quick_phrases")) {
            ToggleSetting(uiText("feedback.quick_sort"), uiText("feedback.quick_sort.hint"), Icons.Default.DragIndicator,
                quickSorting == true, quickSort::choose, position = SettingPosition.FIRST)
            val mediaTitle = uiText("feedback.media_collapse")
            SettingItem(mediaTitle, "", Icons.Default.ViewCompact, SettingPosition.LAST) {
                SettingChoiceGroup(mediaTitle, MediaQuickTextCollapse.entries, mediaCollapse,
                    { copy("feedback.media_collapse." + it.name.lowercase(java.util.Locale.ROOT)) }, onMediaCollapse)
            }
        }
        SettingsGroup(uiText("feedback.back_action")) {
            ToggleSetting(uiText("feedback.double_back"), uiText("feedback.double_back.hint"), Icons.AutoMirrored.Filled.ArrowBack,
                doubleBack != false, homeExit::choose)
        }
    }
}
