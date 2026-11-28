package com.kigtts.next.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Search
import com.kigtts.next.ui.navigation.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.MotionColumn
import com.kigtts.next.ui.components.ReadablePage
import com.kigtts.next.ui.plugins.MarketContentReveal
import com.kigtts.plugin.api.PluginContext
import com.kigtts.plugin.api.PluginRegistry
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.kigtts.next.i18n.LocalUiLocale
import com.kigtts.next.i18n.uiCopy
import com.kigtts.next.i18n.uiText
import com.kigtts.next.theme.ThemeIcon

@Composable
internal fun SettingsScreen(open: (Destination) -> Unit, registry: PluginRegistry? = null,
    context: PluginContext = PluginContext("", ""), revision: Int = 0,
    receiving: (@Composable (SettingPosition) -> Unit)? = null, personalInformation: @Composable () -> Unit = {}) {
    val androidContext = LocalContext.current
    val locale = LocalUiLocale.current
    val cards = remember(registry, revision, context, androidContext, locale) {
        settingsCards(registry, context) { key, fallback ->
            androidContext.uiCopy(key, locale = locale).takeUnless { it == key } ?: fallback
        }
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    val settled = LocalPageSettled.current
    var entriesReady by rememberSaveable { mutableStateOf(settled) }
    LaunchedEffect(settled) {
        if (settled && !entriesReady) {
            withFrameNanos { }
            entriesReady = true
        }
    }
    RegisterPageActions(listOf(ToolbarAction("search-settings", uiText("settings.search"), Icons.Default.Search, click = { searching = true })))
    if (searching) SettingsSearchDialog(cards, { searching = false }, open)
    ReadablePage(840.dp) {
        LazyColumn(Modifier.fillMaxSize().testTag("settings-list"), contentPadding = pageContentPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item("settings-profile") { personalInformation() }
            items(cards, key = { it.id }) { card ->
                SettingsGroup(card.title, card.subtitle) {
                    MarketContentReveal(entriesReady, placeholder = {
                        Box(Modifier.fillMaxWidth().height(card.links.sumOf { if (it.summary.isEmpty()) 56 else 72 }.dp))
                    }) {
                        MotionColumn(card.links, { link, _ -> link.id }, Modifier.fillMaxWidth(),
                            spacing = 2.dp, animateInitial = false) { link, enabled ->
                            val index = card.links.indexOfFirst { it.id == link.id }
                            val position = when {
                                card.links.size == 1 || index < 0 -> SettingPosition.SINGLE
                                index == 0 -> SettingPosition.FIRST
                                index == card.links.lastIndex -> SettingPosition.LAST
                                else -> SettingPosition.MIDDLE
                            }
                            if (link.id == "face-receive" && receiving != null) receiving(position)
                            else SettingItem(link.title, link.summary, link.icon, position,
                                Modifier.testTag("settings-entry/${link.id}"), onClick = { open(link.destination) }, enabled = enabled, badge = link.badge,
                                trailing = { ThemeIcon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) })
                        }
                    }
                }
            }
        }
    }
}
