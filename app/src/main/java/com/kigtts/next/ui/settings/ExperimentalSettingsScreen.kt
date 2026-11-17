package com.kigtts.next.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import com.kigtts.next.i18n.uiText
import com.kigtts.next.manager.ExperimentalTool
import com.kigtts.next.state.AppState
import com.kigtts.next.state.AppViewModel

@Composable
internal fun ExperimentalSettingsScreen(state: AppState, model: AppViewModel) {
    SettingsLayout {
        SettingsGroup(uiText("experiments.media")) {
            ToggleSetting(uiText("experiments.live.title"), uiText("experiments.live.summary"),
                Icons.Default.MotionPhotosOn, state.appleLivePhotos, model::appleLivePhotos, modifier = Modifier.testTag("apple-live-photos"))
        }
        SettingsGroup(uiText("experiments.tools")) {
            ExperimentalTool.entries.forEach { tool ->
                val enabled = model.experimentalToolEnabled(tool)
                val busy = tool.id in state.busyPlugins
                val error = model.pluginManager.bundledErrors.containsKey(tool.id) ||
                    model.pluginManager.status.any { it.installed.manifest.id == tool.id && it.error != null }
                ToggleSetting(uiText("experiments.${tool.key}.title"),
                    uiText(if (enabled && error) "experiments.failed" else "experiments.${tool.key}.summary"),
                    if (tool == ExperimentalTool.ASSISTANT) Icons.Default.AutoAwesome else Icons.Default.Code,
                    enabled, { model.setExperimentalTool(tool, it) }, modifier = Modifier.testTag("experiment-${tool.key}"), enabled = !busy)
            }
        }
    }
}
