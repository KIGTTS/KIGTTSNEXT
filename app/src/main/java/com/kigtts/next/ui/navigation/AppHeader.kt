package com.kigtts.next.ui.navigation

import com.kigtts.next.i18n.uiText
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.compactPlainIconColors
import com.kigtts.next.R
import com.kigtts.next.state.*
import com.kigtts.next.ui.plugins.SlotActions
import com.kigtts.plugin.api.UiSlot
import com.kigtts.next.ui.home.homePreviewRegion

@Composable
fun AppHeader(state: AppState, model: AppViewModel, openSettings: () -> Unit) {
    val home = state.destination == Destination.Home
    val recovery by model.safeModeSession.state.collectAsStateWithLifecycle()
    val title = when (val destination = state.destination) {
        Destination.Home -> "KIGTTS"
        Destination.Settings -> uiText("nav.settings")
        Destination.PersonalCards -> uiText("nav.profile")
        Destination.PersonalInformationEditor -> uiText("nav.profile")
        Destination.ContactFavorites -> uiText("nav.favorites")
        is Destination.ContactFavoriteDetail -> uiText("nav.card_details")
        Destination.Fonts -> uiText("nav.fonts")
        is Destination.SettingsDetail -> uiText("settings.category.${destination.category.name.lowercase()}")
        Destination.Plugins -> uiText("nav.plugins")
        is Destination.PluginPage -> model.pluginPage(destination.pluginId, destination.pageId)?.title ?: uiText("nav.plugin_page")
        is Destination.MediaLibrary -> uiText("nav.media")
        is Destination.HtmlFiles -> uiText("nav.page_files")
        is Destination.HtmlPreview -> uiText("nav.page_preview")
        is Destination.QuickTextReview -> uiText("nav.change_preview")
        is Destination.ContentEditor -> uiText("nav.content_editor")
        is Destination.PhraseEditor -> uiText("nav.edit_phrase")
        is Destination.CardEditor -> uiText("nav.edit_image")
        is Destination.CardPreview -> uiText("nav.photo_preview")
        Destination.Cards -> uiText("nav.album")
        is Destination.CardCamera -> uiText("nav.camera")
        is Destination.StickerReview -> uiText("nav.change_preview")
        is Destination.StickerLibrary -> uiText("nav.stickers")
    }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (home) com.kigtts.next.ui.home.AutomaticReadButton(state, model)
            else IconButton(onClick = feedbackClick(model::navigateBack),
                modifier=Modifier.testTag("auto-playback"), colors = compactPlainIconColors()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, uiText("nav.back"))
            }
            if (home) SlotActions(UiSlot.HEADER_START, model.registry, model::invoke, Modifier.weight(1f),
                context = model.context(), revision = state.pluginRevision)
        }
        if (home) SafeModeBrand(recovery.active, Modifier.homePreviewRegion("header-logo"))
        else Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (home) SlotActions(UiSlot.HEADER_END, model.registry, model::invoke, Modifier.weight(1f),
                context = model.context(), revision = state.pluginRevision)
            if (home) IconButton(onClick = feedbackClick(openSettings), modifier = Modifier.testTag("open-settings").homePreviewRegion("header-end"),
                colors = compactPlainIconColors()) { Icon(Icons.Default.Settings, uiText("nav.open_settings")) }
        }
    }
}
