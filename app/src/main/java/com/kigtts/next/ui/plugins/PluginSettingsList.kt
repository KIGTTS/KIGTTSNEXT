package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.ui.components.*
import com.kigtts.next.ui.navigation.LocalPageActive
import com.kigtts.next.ui.settings.*
import com.kigtts.plugin.api.*

/** Small declarative settings lists share the native settings components, without a new SDK type.
 * Resource collections and large voice catalogs retain the virtualized management renderer. */
internal fun ManagementList.usesSettingsLayout() = reorderAction == null && filters.isEmpty() &&
    items.size in 1..32 && items.all { it.actions.isEmpty() && it.image == null && !it.pinned }

internal fun settingPosition(index: Int, count: Int) = when {
    count <= 1 -> SettingPosition.SINGLE
    index == 0 -> SettingPosition.FIRST
    index == count - 1 -> SettingPosition.LAST
    else -> SettingPosition.MIDDLE
}

@Composable
internal fun PluginSettingsList(list: ManagementList, busy: Boolean,
    invoke: (String, Map<String, String>) -> Unit, hasHeader: Boolean,
    header: @Composable ColumnScope.() -> Unit) {
    val enabled = !busy && LocalPageActive.current
    val groups = list.items.groupBy { it.group.ifBlank { "设置" } }.entries.toList()
    SettingsLayout {
        if (hasHeader) header()
        MotionColumn(groups, { group, _ -> group.key }, enabled = enabled, spacing = 20.dp,animateInitial=false,
            modifier = Modifier.testTag("managed-list/" + list.id)) { group, active ->
            SettingsGroup(group.key) {
                MotionColumn(group.value, { item, _ -> item.id }, enabled = active, spacing = 2.dp,animateInitial=false) { item, interactive ->
                    val toggle = item.toggle
                    SettingItem(item.title, item.subtitle, item.icon.vector(),
                        settingPosition(group.value.indexOfFirst { it.id == item.id }, group.value.size),
                        modifier = Modifier.testTag("managed-item/" + item.id), enabled = interactive,
                        onClick = item.primaryAction?.let { action -> { invoke(action, emptyMap()) } }, trailing = {
                            if (toggle != null) Switch(toggle.checked,
                                onCheckedChange = feedbackChange { invoke(toggle.action, mapOf("checked" to it.toString())) },
                                enabled = interactive, colors = SwitchDefaults.colors().keepColorsWhenLocked(),
                                modifier = Modifier.testTag("managed-toggle/" + item.id))
                            else {
                                PageContentMotion(item.selected) { selected ->
                                    if (selected) Icon(Icons.Default.Check, "当前选用", Modifier.size(24.dp))
                                    else if (item.primaryAction != null)
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
                                }
                            }
                        })
                }
            }
        }
    }
}
