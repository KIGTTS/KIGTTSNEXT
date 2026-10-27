package com.kigtts.next.ui.navigation

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
            Destination.Settings -> "设置"
            Destination.PersonalCards -> "个人信息"
            Destination.ContactFavorites -> "扩列收藏夹"
            Destination.Fonts -> "字体"
            is Destination.SettingsDetail -> destination.category.title
            Destination.Plugins -> "插件管理"
            is Destination.PluginPage -> model.pluginPage(destination.pluginId, destination.pageId)?.title ?: "插件页面"
            is Destination.MediaLibrary -> if (destination.target.startsWith("phrase:") && phraseDraft?.type == QuickCardType.PAGE) "页面库" else "媒体库"
            is Destination.HtmlFiles -> "页面文件"
            is Destination.HtmlPreview -> "页面预览"
            is Destination.QuickTextReview -> "修改预览"
            is Destination.ContentEditor -> model.content.editorDraft.value?.let { if(it.html)it.currentTitle else "文本编辑器" } ?: "内容编辑器"
            is Destination.PhraseEditor -> if (destination.adding) "添加快捷文本" else "编辑快捷文本"
            is Destination.CardEditor -> "编辑拍立得"
            is Destination.CardPreview -> "拍立得预览"
            Destination.Cards -> "拍立得"
            is Destination.CardCamera -> "拍摄"
            is Destination.StickerReview -> "修改预览"
            is Destination.StickerLibrary -> "贴纸库"
        }
    if (destination == Destination.Cards) {
        CardPageScaffold(title, model::navigateBack) { CardHostScreen(model) }
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
    PageScaffold(title,model::navigateBack,largeBar=destination !is Destination.CardCamera && !chatPage,
        backTag=if(destination is Destination.PhraseEditor)"cancel-phrase-details" else "page-back",
        subtitle = if (destination == Destination.Plugins) "管理已安装的插件" else null,
        extendToBottom = destination == Destination.Settings || destination is Destination.SettingsDetail ||
            destination == Destination.Fonts || destination == Destination.PersonalCards || destination == Destination.ContactFavorites || destination == Destination.Plugins ||
            (destination is Destination.PluginPage && !chatPage && pluginPage?.blocks?.none {
                it is com.kigtts.plugin.api.PageBlock.Browser || it is com.kigtts.plugin.api.PageBlock.Files || it is com.kigtts.plugin.api.PageBlock.SourceEditor
            } == true),
        backgroundSettings = state.theme,
        contentScrollsHeader = destination != Destination.SettingsDetail(SettingsCategory.APPEARANCE)) {
            when (destination) {
                Destination.Home -> Unit
                Destination.Settings -> SettingsScreen(model::navigate, model.registry, model.context(), state.pluginRevision) { com.kigtts.next.ui.identity.PersonalInformationEntry(model) }
                Destination.PersonalCards -> com.kigtts.next.ui.identity.PersonalCardsScreen(model)
                Destination.ContactFavorites -> com.kigtts.next.ui.identity.ContactFavoritesScreen(model)
                Destination.Fonts -> FontSettingsScreen(state.theme, model::theme,
                    fromStore = if (model.registry.plugins.any { it.descriptor.id == com.kigtts.next.plugins.control.STORE_ID && model.registry.available(it) })
                        ({ model.invoke(com.kigtts.next.plugins.control.STORE_ID, "font-library") }) else null)
                is Destination.SettingsDetail -> when (destination.category) {
                    SettingsCategory.APPEARANCE -> AppearanceSettingsScreen(state.theme, model::theme, state.subtitlePlaceholder, model::setSubtitlePlaceholder,
                        state.subtitleAppearance, model::setSubtitleAppearance, onFonts = { model.navigate(Destination.Fonts) })
                    SettingsCategory.FEEDBACK -> FeedbackSettingsScreen(state.hapticsEnabled, model::haptics, model.quickSort, model.homeExit)
                    SettingsCategory.OVERLAY -> OverlaySettingsScreen()
                    SettingsCategory.EXPERIMENTAL -> ExperimentalSettingsScreen(state.appleLivePhotos, model::appleLivePhotos)
                    SettingsCategory.WEB -> WebSettingsScreen()
                    SettingsCategory.BACKUP_RESTORE -> BackupRestoreScreen(model)
                    SettingsCategory.CAMERA -> CameraSettingsScreen()
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
                is Destination.PluginPage -> PluginPageScreen(model.pluginPage(destination.pluginId, destination.pageId), destination.pluginId in state.busyPlugins,
                    resolve = { resource -> model.pluginManager.paths.existingResource(resource.pluginId ?: destination.pluginId, resource.path) },
                    browser = { block -> PluginBrowserScreen(model, destination.pluginId, destination.pageId, block) },
                    historyCache = model.pluginChatHistory, historyKey = destination.pluginId + "/") { action, inputs ->
                    model.invoke(destination.pluginId, action, inputs)
                }
            }
    }
}
