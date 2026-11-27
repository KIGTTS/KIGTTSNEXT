package com.kigtts.next.ui.navigation

import com.kigtts.next.i18n.UiLanguage
import com.kigtts.next.i18n.uiText
import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.state.*
import com.kigtts.next.ui.plugins.*
import com.kigtts.next.ui.settings.*
import com.kigtts.next.ui.media.*
import com.kigtts.next.ui.home.PhraseEditorScreen
import com.kigtts.next.ui.cards.CardHostScreen
import com.kigtts.next.ui.cards.CardPageScaffold
import com.kigtts.next.plugins.control.STORE_ID
import com.kigtts.plugin.api.PluginPage

private class MarketPageSnapshot {
    var initialized = false
    var page: PluginPage? = null
}

/** Page content is independent of its lifetime and transition in the navigation host. */
@Composable
fun SecondaryDestination(destination: Destination, state: AppState, model: AppViewModel) {
    val language = if (state.followsSystemUiLanguage) UiLanguage.SYSTEM else UiLanguage.fromTag(state.uiLocale)
    val marketPage = destination is Destination.PluginPage && destination.pluginId == STORE_ID
    val pluginPage = (destination as? Destination.PluginPage)?.let {
        val cacheBytes = if (it.pluginId == "service.speech-synthesis" && it.pageId == "manager")
            model.readingCacheBytes?.collectAsStateWithLifecycle()?.value else null
        // Built-in pages derive controls from the current context. Do not rebuild their lists
        // just because a parent animates, the theme changes, or the page moves to the back stack.
        val snapshot = if (marketPage) remember(model, it) { MarketPageSnapshot() } else null
        if (snapshot?.initialized == true && !LocalPageActive.current) snapshot.page
        else {
            val context = model.context()
            val page = remember(model, it, state.pluginRevision, context, cacheBytes) { model.pluginPage(it.pluginId, it.pageId) }
            if (snapshot != null) SideEffect { snapshot.page = page; snapshot.initialized = true }
            page
        }
    }
    val chatHandlesIme = destination.assistantChatHandlesIme()
    val editorSigningPage = destination == Destination.PluginPage(PLUGIN_EDITOR_ID,
        com.kigtts.next.plugins.editor.EDITOR_SIGNING_PAGE)
    val editorProject = destination is Destination.PluginPage && destination.pageId.startsWith("project/") &&
        destination.pluginId in setOf(PLUGIN_EDITOR_ID, NATIVE_BUILDER_ID) && pluginPage?.header != null
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
            is Destination.PluginPage -> when {
                editorProject -> ""
                editorSigningPage -> uiText("editor.signatures")
                else -> pluginPage?.title ?: uiText(if (destination.pluginId == com.kigtts.next.plugins.control.STORE_ID)
                    "market.name" else "nav.plugin_page")
            }
            is Destination.MediaLibrary -> {
                val draft by model.content.phraseDraft.collectAsStateWithLifecycle()
                uiText(if (destination.target.startsWith("phrase:") && draft?.type == QuickCardType.PAGE) "nav.pages" else "nav.media")
            }
            is Destination.HtmlFiles -> uiText("nav.page_files")
            is Destination.HtmlPreview -> uiText("nav.page_preview")
            is Destination.QuickTextReview -> uiText("nav.change_preview")
            is Destination.ContentEditor -> model.content.editorDraft.value?.let { if(it.html)it.currentTitle else uiText("nav.text_editor") } ?: uiText("nav.content_editor")
            is Destination.PhraseEditor -> if (destination.adding) uiText("nav.add_phrase") else uiText("nav.edit_phrase")
            is Destination.CardEditor -> uiText("nav.edit_image")
            is Destination.CardPreview -> uiText("nav.photo_preview")
            Destination.Cards -> uiText("nav.album")
            is Destination.CardCamera -> uiText("nav.camera")
            Destination.CameraExpressionManagement -> uiText("expression.title")
            is Destination.CameraExpressionEditor -> uiText(if (destination.effectId == null) "expression.new" else "expression.edit")
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
    if (destination is Destination.PluginPage && pluginPage?.search != null) {
        PluginPageScreen(pluginPage, destination.pluginId in state.busyPlugins,
            resolve = { model.pluginManager.paths.existingResource(it.pluginId ?: destination.pluginId, it.path) },
            onBack = model::navigateBack, progressiveMarket = marketPage) { action, inputs -> model.invoke(destination.pluginId, action, inputs) }
        return
    }
    val chatPage = pluginPage?.blocks?.any { it is com.kigtts.plugin.api.PageBlock.Chat } == true
    if (destination == Destination.SettingsDetail(SettingsCategory.ABOUT)) {
        AboutPage(model::navigateBack, openLink = { url ->
            model.externalLinks.offer(com.kigtts.next.links.ExternalLink(url = url, mode = com.kigtts.next.links.WebOpenMode.CUSTOM_TAB))
        }, openDocument = { model.navigate(Destination.SettingsDetail(it)) }, settings = state.theme)
        return
    }
    PageScaffold(title,model::navigateBack,largeBar=destination !is Destination.CardCamera && destination !is Destination.ContactFavoriteDetail &&
        destination !is Destination.CameraExpressionEditor && !chatPage && !editorProject,
        backTag=if(destination is Destination.PhraseEditor)"cancel-phrase-details" else "page-back",
        subtitle = if (destination == Destination.Plugins) uiText("settings.plugins.subtitle") else null,
        extendToBottom = destination == Destination.Settings || destination is Destination.SettingsDetail ||
            destination == Destination.Fonts || destination == Destination.PersonalCards || destination == Destination.PersonalInformationEditor ||
            destination == Destination.ContactFavorites || destination is Destination.ContactFavoriteDetail ||
            destination == Destination.Plugins ||
            (destination is Destination.PluginPage && !chatPage && pluginPage?.blocks?.none {
                it is com.kigtts.plugin.api.PageBlock.Browser || it is com.kigtts.plugin.api.PageBlock.Files || it is com.kigtts.plugin.api.PageBlock.SourceEditor
            } == true),
        backgroundSettings = com.kigtts.next.theme.LocalAppAppearanceSettings.current,
        backdropMovesWithPage = marketPage,
        contentHandlesIme = chatHandlesIme,
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
                        languageName = uiLanguageLabel(language),
                        onLanguage = { model.navigate(Destination.SettingsDetail(SettingsCategory.LANGUAGE)) })
                    SettingsCategory.LANGUAGE -> UiLanguageScreen(language, model::setUiLanguage)
                    SettingsCategory.QUICK_ACTIONS -> QuickActionsSettings(model)
                    SettingsCategory.FEEDBACK -> FeedbackSettingsScreen(state.hapticsEnabled, model::haptics, model.quickSort, model.homeExit,
                        state.mediaQuickTextCollapse, model::mediaQuickTextCollapse)
                    SettingsCategory.OVERLAY -> OverlaySettingsScreen()
                    SettingsCategory.EXPERIMENTAL -> ExperimentalSettingsScreen(state, model)
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
                Destination.CameraExpressionManagement -> com.kigtts.next.ui.cards.camera.CameraExpressionManagementPage(model)
                is Destination.CameraExpressionEditor -> com.kigtts.next.ui.cards.camera.CameraExpressionEditPage(model, destination.effectId)
                is Destination.CardPreview -> com.kigtts.next.ui.cards.CardPreviewScreen(model.cards, model.stickers, destination.cardId,
                    { model.navigate(Destination.CardEditor(it)) }, model::message, onQuickImage = model::quickImageCard)
                Destination.Cards -> Unit // Handled by the window-adaptive scaffold above.
                is Destination.StickerReview -> com.kigtts.next.ui.cards.StickerReviewScreen(model, destination.token)
                is Destination.StickerLibrary -> com.kigtts.next.ui.cards.StickerLibraryScreen(model.cards, model.stickers, model::message,
                    onQuickImage = model::quickImageCard, requestedLibrary = destination.libraryId)
                is Destination.PluginPage -> {
                    val nativeEditorActive = destination.pluginId == PLUGIN_EDITOR_ID && destination.pageId == EDITOR_MANAGER_PAGE
                    if (editorSigningPage) {
                        com.kigtts.next.ui.plugins.editor.EditorSigningScreen(model)
                    } else {
                    var creating by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    val projectHeader = if (editorProject) pluginPage?.header else null
                    val busy = destination.pluginId in state.busyPlugins ||
                        (nativeEditorActive && NATIVE_BUILDER_ID in state.busyPlugins)
                    val resolve: (com.kigtts.plugin.api.PluginResource) -> java.io.File? = { resource ->
                        model.pluginManager.paths.existingResource(resource.pluginId ?: destination.pluginId, resource.path)
                    }
                    PluginPageScreen(if (projectHeader == null) pluginPage else pluginPage?.copy(header = null, search = pluginPage.search), busy,
                    resolve = resolve,
                    browser = { block -> PluginBrowserScreen(model, destination.pluginId, destination.pageId, block) },
                    historyCache = model.pluginChatHistory, historyKey = destination.pluginId + "/",
                    extraSettings = {
                        if (projectHeader != null) com.kigtts.next.ui.plugins.editor.EditorProjectHeader(
                            model, destination.pluginId, projectHeader, busy, resolve)
                        if (destination.pluginId == "service.speech-recognition" && destination.pageId == "manager")
                            com.kigtts.next.ui.home.CaptionWindowSetting(model.captionServiceReady, model::openCaptionWindow)
                    },
                    startVoiceInput = (model::startChatVoiceInput).takeIf { destination.pluginId == "tools.cloud-agent" && model.captionServiceReady },
                    followIme = chatHandlesIme, progressiveMarket = marketPage) { action, inputs ->
                    val nativeAction = editorNativeAction(destination.pluginId, destination.pageId, action)
                    when {
                        nativeEditorActive && action == "create" -> creating = true
                        nativeEditorActive && action == "editor-signatures" -> model.navigate(Destination.PluginPage(
                            PLUGIN_EDITOR_ID, com.kigtts.next.plugins.editor.EDITOR_SIGNING_PAGE))
                        nativeAction != null -> model.invoke(NATIVE_BUILDER_ID, nativeAction, inputs)
                        else -> model.invoke(destination.pluginId, action, inputs)
                    }
                    }
                    if (creating) com.kigtts.next.ui.plugins.editor.EditorCreationDialog(model) { creating = false }
                    }
                }
            }
    }
}

internal fun Destination.assistantChatHandlesIme() = this is Destination.PluginPage && pluginId == "tools.cloud-agent" && pageId == "chat"
