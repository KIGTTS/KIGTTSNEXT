package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.MotionColumn
import com.kigtts.plugin.api.PluginContext
import com.kigtts.plugin.api.PluginRegistry
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(open: (Destination) -> Unit, registry: PluginRegistry? = null,
    context: PluginContext = PluginContext("", ""), revision: Int = 0, personalInformation: @Composable () -> Unit = {}) {
    val cards = remember(registry, revision, context) { settingsCards(registry, context) }
    SettingsLayout {
        personalInformation()
        MotionColumn(cards, { card, _ -> card.id }, Modifier.fillMaxWidth(), spacing = 20.dp, animateInitial = false) { card, active ->
            SettingsGroup(card.title, card.subtitle) {
                MotionColumn(card.links, { link, _ -> link.id }, Modifier.fillMaxWidth(), enabled = active,
                    spacing = 2.dp, animateInitial = false) { link, enabled ->
                    val index = card.links.indexOfFirst { it.id == link.id }
                    val position = when {
                        card.links.size == 1 || index < 0 -> SettingPosition.SINGLE
                        index == 0 -> SettingPosition.FIRST
                        index == card.links.lastIndex -> SettingPosition.LAST
                        else -> SettingPosition.MIDDLE
                    }
                    SettingItem(link.title, link.summary, link.icon, position,
                        Modifier.testTag("settings-entry/${link.id}"), onClick = { open(link.destination) }, enabled = enabled, badge = link.badge,
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
                }
            }
        }
    }
}
