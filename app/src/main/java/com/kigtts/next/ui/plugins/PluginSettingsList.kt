package com.kigtts.next.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    items.size in 1..32 && items.all { it.actions.isEmpty() && it.badges.isEmpty() && (it.image == null || it.group.isNotBlank()) && !it.pinned }

internal fun settingPosition(index: Int, count: Int) = when {
    count <= 1 -> SettingPosition.SINGLE
    index == 0 -> SettingPosition.FIRST
    index == count - 1 -> SettingPosition.LAST
    else -> SettingPosition.MIDDLE
}

@Composable
internal fun PluginSettingsList(list: ManagementList, busy: Boolean,
    invoke: (String, Map<String, String>) -> Unit, hasHeader: Boolean, resolve: (PluginResource) -> java.io.File? = { null },
    header: @Composable ColumnScope.() -> Unit) {
    val enabled = LocalPageActive.current
    val invokeWhenReady: (String, Map<String, String>) -> Unit = { action, inputs -> if (enabled && !busy) invoke(action, inputs) }
    val groups = list.items.groupBy { it.group.ifBlank { "设置" } }.entries.toList()
    SettingsLayout {
        if (hasHeader) header()
        MotionColumn(groups, { group, _ -> group.key }, enabled = enabled, spacing = 20.dp,animateInitial=false,
            modifier = Modifier.testTag("managed-list/" + list.id)) { group, active ->
            SettingsGroup(group.key) {
                MotionColumn(group.value, { item, _ -> item.id }, enabled = active, spacing = 2.dp,animateInitial=false) { item, interactive ->
                    val toggle = item.toggle
                    SettingItem(item.title, item.subtitle, if (item.icon == PluginIcon.NONE && item.symbol == null) null else item.icon.vector(item.symbol),
                        settingPosition(group.value.indexOfFirst { it.id == item.id }, group.value.size),
                        modifier = Modifier.testTag("managed-item/" + item.id), enabled = interactive,
                        leading = if (item.image == null) null else ({ com.kigtts.next.ui.management.ResourceAvatar(item, resolve) }),
                        onClick = item.primaryAction?.let { action -> { invokeWhenReady(action, emptyMap()) } }, trailing = {
                            if (toggle != null || item.selection != null || item.selected)
                                com.kigtts.next.ui.management.ManagedItemControl(item, interactive, invokeWhenReady)
                            else if (item.primaryAction != null)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
                        })
                }
            }
        }
    }
}
