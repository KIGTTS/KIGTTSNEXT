package com.kigtts.next.ui.navigation

import com.kigtts.next.i18n.uiText
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.plugins.*
import com.kigtts.next.ui.settings.*
import com.kigtts.next.ui.media.*
import com.kigtts.next.ui.home.PhraseEditorScreen
import com.kigtts.next.ui.cards.CardHostScreen
import com.kigtts.next.ui.cards.CardPageScaffold

/** Page content is independent of its lifetime and transition in the navigation host. */
@Composable
fun SecondaryDestination(destination: Destination, state: AppState, model: AppViewModel) {
    val phraseDraft by model.content.phraseDraft.collectAsStateWithLifecycle()
    val title = when (destination) {
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
            is Destination.MediaLibrary -> if (destination.target.startsWith("phrase:") && phraseDraft?.type == QuickCardType.PAGE) uiText("nav.pages") else uiText("nav.media")
            is Destination.HtmlFiles -> uiText("nav.page_files")
            is Destination.HtmlPreview -> uiText("nav.page_preview")
            is Destination.QuickTextReview -> uiText("nav.change_preview")
            is Destination.ContentEditor -> model.content.editorDraft.value?.let { if(it.html)it.currentTitle else uiText("nav.text_editor") } ?: uiText("nav.content_editor")
            is Destination.PhraseEditor -> if (destination.adding) uiText("nav.add_phrase") else uiText("nav.edit_phrase")
            is Destination.CardEditor -> uiText("nav.edit_image")
            is Destination.CardPreview -> uiText("nav.photo_preview")
            Destination.Cards -> uiText("nav.album")
            is Destination.CardCamera -> uiText("nav.camera")
            is Destination.StickerReview -> uiText("nav.change_preview")
            is Destination.StickerLibrary -> uiText("nav.stickers")
        }
    if (destination == Destination.Cards) {
        com.kigtts.next.ui.album.AlbumHost(model, model::navigateBack)
        return
    }
    if (destination is Destination.CardCamera) {
        com.kigtts.next.ui.cards.camera.PolaroidCameraRoute(model, destination)
        return
    }
    if (destination is Destination.CardEditor) {
        com.kigtts.next.ui.cards.CardEditorRoute(model, destination)
        return
    }
    val pluginPage = (destination as? Destination.PluginPage)?.let { model.pluginPage(it.pluginId, it.pageId) }
    if (destination is Destination.PluginPage && pluginPage?.search != null) {
        PluginPageScreen(pluginPage, destination.pluginId in state.busyPlugins,
            resolve = { model.pluginManager.paths.existingResource(it.pluginId ?: destination.pluginId, it.path) },
            onBack = model::navigateBack) { action, inputs -> model.invoke(destination.pluginId, action, inputs) }
        return
    }
    val chatPage = pluginPage?.blocks?.any { it is com.kigtts.plugin.api.PageBlock.Chat } == true
    if (destination == Destination.SettingsDetail(SettingsCategory.ABOUT)) {
        AboutPage(model::navigateBack, openLink = { url ->
            model.externalLinks.offer(com.kigtts.next.links.ExternalLink(url = url, mode = com.kigtts.next.links.WebOpenMode.CUSTOM_TAB))
        }, openDocument = { model.navigate(Destination.SettingsDetail(it)) }, settings = state.theme)
        return
    }
    PageScaffold(title,model::navigateBack,largeBar=destination !is Destination.CardCamera && destination !is Destination.ContactFavoriteDetail && !chatPage,
        backTag=if(destination is Destination.PhraseEditor)"cancel-phrase-details" else "page-back",
        subtitle = if (destination == Destination.Plugins) uiText("settings.plugins.subtitle") else null,
        extendToBottom = destination == Destination.Settings || destination is Destination.SettingsDetail ||
            destination == Destination.Fonts || destination == Destination.PersonalCards || destination == Destination.PersonalInformationEditor ||
            destination == Destination.ContactFavorites || destination is Destination.ContactFavoriteDetail || destination == Destination.Plugins ||
            (destination is Destination.PluginPage && !chatPage && pluginPage?.blocks?.none {
                it is com.kigtts.plugin.api.PageBlock.Browser || it is com.kigtts.plugin.api.PageBlock.Files || it is com.kigtts.plugin.api.PageBlock.SourceEditor
            } == true),
        backgroundSettings = state.theme,
        contentScrollsHeader = destination != Destination.SettingsDetail(SettingsCategory.APPEARANCE)) {
            when (destination) {
                Destination.Home -> Unit
                Destination.Settings -> SettingsScreen(model::navigate, model.registry, model.context(), state.pluginRevision,
                    receiving = { FaceReceivingSetting(model, it) }) { com.kigtts.next.ui.identity.PersonalInformationEntry(model) }
                Destination.PersonalCards -> com.kigtts.next.ui.identity.PersonalCardsScreen(model)
                Destination.PersonalInformationEditor -> com.kigtts.next.ui.identity.PersonalInformationEditorScreen(model)
                Destination.ContactFavorites -> com.kigtts.next.ui.identity.ContactFavoritesScreen(model)
                is Destination.ContactFavoriteDetail -> com.kigtts.next.ui.identity.ContactFavoriteDetailScreen(model, destination.id)
                Destination.Fonts -> FontSettingsScreen(state.theme, model::theme,
                    fromStore = if (model.registry.plugins.any { it.descriptor.id == com.kigtts.next.plugins.control.STORE_ID && model.registry.available(it) })
                        ({ model.invoke(com.kigtts.next.plugins.control.STORE_ID, "font-library") }) else null)
                is Destination.SettingsDetail -> when (destination.category) {
                    SettingsCategory.APPEARANCE -> AppearanceSettingsScreen(state.theme, model::theme, state.subtitlePlaceholder, model::setSubtitlePlaceholder,
                        state.subtitleAppearance, model::setSubtitleAppearance, onFonts = { model.navigate(Destination.Fonts) },
                        languageName = com.kigtts.next.i18n.UiLanguage.fromTag(state.uiLocale).label,
                        onLanguage = { model.navigate(Destination.SettingsDetail(SettingsCategory.LANGUAGE)) })
                    SettingsCategory.LANGUAGE -> UiLanguageScreen(state.uiLocale, model::setUiLanguage)
                    SettingsCategory.FEEDBACK -> FeedbackSettingsScreen(state.hapticsEnabled, model::haptics, model.quickSort, model.homeExit)
                    SettingsCategory.OVERLAY -> OverlaySettingsScreen()
                    SettingsCategory.EXPERIMENTAL -> ExperimentalSettingsScreen(state.appleLivePhotos, model::appleLivePhotos)
                    SettingsCategory.WEB -> WebSettingsScreen()
                    SettingsCategory.BACKUP_RESTORE -> BackupRestoreScreen(model)
                    SettingsCategory.CAMERA -> CameraSettingsScreen { model.navigate(Destination.SettingsDetail(SettingsCategory.PHOTO_WEATHER)) }
                    SettingsCategory.PHOTO_WEATHER -> PhotoWeatherSettingsScreen()
                    SettingsCategory.ABOUT -> AboutScreen(openLink = { url ->
                        model.externalLinks.offer(com.kigtts.next.links.ExternalLink(url = url,
                            mode = com.kigtts.next.links.WebOpenMode.CUSTOM_TAB))
                    }, openDocument = { model.navigate(Destination.SettingsDetail(it)) })
                    SettingsCategory.USER_AGREEMENT, SettingsCategory.PRIVACY_POLICY -> AboutDocumentScreen(destination.category)
                    SettingsCategory.OPEN_SOURCE_LICENSES -> AboutLicensesScreen()
                }
                Destination.Plugins -> PluginsScreen(model)
                is Destination.MediaLibrary -> MediaLibraryScreen(model, destination.target)
                is Destination.HtmlFiles -> HtmlFilesScreen(model, destination.mediaId, destination.target)
                is Destination.HtmlPreview -> model.mediaRepository.get(destination.mediaId)?.let { MediaWeb(it,state,model,androidx.compose.ui.Modifier.fillMaxSize()) }
                is Destination.QuickTextReview -> QuickTextReviewScreen(model,destination.token)
                is Destination.ContentEditor -> ContentEditorScreen(model, destination.token)
                is Destination.PhraseEditor -> PhraseEditorScreen(model, destination.token)
                is Destination.CardEditor -> Unit // Shares the album's adaptive chrome above.
                is Destination.CardCamera -> Unit // Native camera/printing route handled above.
                is Destination.CardPreview -> com.kigtts.next.ui.cards.CardPreviewScreen(model.cards, model.stickers, destination.cardId,
                    { model.navigate(Destination.CardEditor(it)) }, model::message, onQuickImage = model::quickImageCard)
                Destination.Cards -> Unit // Handled by the window-adaptive scaffold above.
                is Destination.StickerReview -> com.kigtts.next.ui.cards.StickerReviewScreen(model, destination.token)
                is Destination.StickerLibrary -> com.kigtts.next.ui.cards.StickerLibraryScreen(model.cards, model.stickers, model::message,
                    onQuickImage = model::quickImageCard, requestedLibrary = destination.libraryId)
                is Destination.PluginPage -> {
                    if (destination.pluginId == "service.speech-synthesis" && destination.pageId == "manager")
                        model.readingCacheBytes?.collectAsStateWithLifecycle()?.value
                    PluginPageScreen(model.pluginPage(destination.pluginId, destination.pageId), destination.pluginId in state.busyPlugins,
                    resolve = { resource -> model.pluginManager.paths.existingResource(resource.pluginId ?: destination.pluginId, resource.path) },
                    browser = { block -> PluginBrowserScreen(model, destination.pluginId, destination.pageId, block) },
                    historyCache = model.pluginChatHistory, historyKey = destination.pluginId + "/",
                    extraSettings = {
                        if (destination.pluginId == "service.speech-recognition" && destination.pageId == "manager")
                            com.kigtts.next.ui.home.CaptionWindowSetting(model::openCaptionWindow)
                    }) { action, inputs ->
                    model.invoke(destination.pluginId, action, inputs)
                    }
                }
            }
    }
}
