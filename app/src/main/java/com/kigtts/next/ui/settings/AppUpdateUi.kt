package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kigtts.next.i18n.uiText
import com.kigtts.next.ui.components.MotionAlertDialog
import com.kigtts.next.ui.components.dialogFeedbackClick
import com.kigtts.next.updates.AppUpdates
import com.kigtts.next.updates.OFFICIAL_WEBSITE
import com.kigtts.next.updates.UpdateResult
import kotlinx.coroutines.delay

internal val LocalAppUpdates = staticCompositionLocalOf<AppUpdates?> { null }

@Composable
internal fun AppUpdateHost(updates: AppUpdates, ready: Boolean, openLink: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(updates, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(2_000)
            updates.check()
        }
    }
    val state by updates.state.collectAsStateWithLifecycle()
    val release = state.release
    var revealed by remember(release?.version, state.pending) { mutableStateOf(false) }
    if (release == null || !state.pending || !revealed && !state.manual && !ready) return
    SideEffect { revealed = true }
    MotionAlertDialog(onDismissRequest = updates::later,
        title = { Text(uiText("about.update.title", "version" to release.version.toString())) },
        text = {
            Text(release.notes.ifBlank { uiText("about.update.no_notes") },
                Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
        },
        dismissButton = {
            TextButton(dialogFeedbackClick(updates::ignore)) { Text(uiText("about.update.ignore")) }
            TextButton(dialogFeedbackClick(updates::later)) { Text(uiText("about.update.later")) }
        },
        confirmButton = {
            Button(dialogFeedbackClick { updates.later(); openLink(OFFICIAL_WEBSITE) }) {
                Text(uiText("about.update.open"))
            }
        })
}

@Composable
internal fun AboutUpdateSettings(updates: AppUpdates) {
    val state by updates.state.collectAsStateWithLifecycle()
    val release = state.release
    val summary = when {
        state.result == UpdateResult.CHECKING -> uiText("about.update.checking")
        release != null -> uiText("about.update.title", "version" to release.version.toString())
        state.result == UpdateResult.CURRENT -> uiText("about.update.current")
        state.result == UpdateResult.FAILED -> uiText("about.update.failed")
        state.result == UpdateResult.IGNORED -> uiText("about.update.ignored")
        else -> ""
    }
    SettingsGroup(uiText("about.update.group")) {
        ToggleSetting(uiText("about.update.automatic"), uiText("about.update.automatic_hint"),
            Icons.Default.Sync, state.automatic, { enabled ->
                updates.automatic(enabled)
                if (enabled) updates.check()
            }, position = SettingPosition.FIRST)
        SettingItem(uiText(if (state.release == null) "about.update.check" else "about.update.details"),
            summary, Icons.Default.SystemUpdate, SettingPosition.LAST, Modifier.testTag("about-check-update"),
            enabled = state.result != UpdateResult.CHECKING,
            onClick = { if (state.release == null) updates.check(manual = true) else updates.showAvailable() })
    }
}
