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

/** Page content is independent of its lifetime and transition in the navigation host. */
@Composable
fun SecondaryDestination(destination: Destination, state: AppState, model: AppViewModel) {
    val phraseDraft by model.content.phraseDraft.collectAsStateWithLifecycle()
    val cameraPhoto by model.cardCapture.result.collectAsStateWithLifecycle()
    val title = when (destination) {
            Destination.Home -> "KIGTTS"
            Destination.Settings -> "设置"
            is Destination.SettingsDetail -> destination.category.title
            Destination.Plugins -> "插件管理"
            is Destination.PluginPage -> model.registry.plugin(destination.pluginId)?.page(destination.pageId, model.context())?.title ?: "插件页面"
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
    PageScaffold(title,model::navigateBack,largeBar=destination !is Destination.CardCamera,
        backTag=if(destination is Destination.PhraseEditor)"cancel-phrase-details" else "page-back") {
            when (destination) {
                Destination.Home -> Unit
                Destination.Settings -> SettingsScreen(model::navigate, model.registry, model.context(), state.pluginRevision)
                is Destination.SettingsDetail -> when (destination.category) {
                    SettingsCategory.APPEARANCE -> AppearanceSettingsScreen(state.theme, model::theme, state.subtitlePlaceholder, model::setSubtitlePlaceholder)
                    SettingsCategory.FEEDBACK -> FeedbackSettingsScreen(state.hapticsEnabled, model::haptics)
                    SettingsCategory.EXPERIMENTAL -> ExperimentalSettingsScreen(state.appleLivePhotos, model::appleLivePhotos)
                    SettingsCategory.WEB -> WebSettingsScreen()
                }
                Destination.Plugins -> PluginsScreen(model)
                is Destination.MediaLibrary -> MediaLibraryScreen(model, destination.target)
                is Destination.HtmlFiles -> HtmlFilesScreen(model, destination.mediaId, destination.target)
                is Destination.HtmlPreview -> model.mediaRepository.get(destination.mediaId)?.let { MediaWeb(it,state,model,androidx.compose.ui.Modifier.fillMaxSize()) }
                is Destination.QuickTextReview -> QuickTextReviewScreen(model,destination.token)
                is Destination.ContentEditor -> ContentEditorScreen(model, destination.token)
                is Destination.PhraseEditor -> PhraseEditorScreen(model, destination.token)
                is Destination.CardEditor -> com.kigtts.next.ui.cards.CardEditorScreen(model.cards, model.stickers, destination.cardId, model::message,
                    openLibrary = { model.navigate(Destination.StickerLibrary()) }, openCamera = { model.cardCapture.open(destination.cardId) },
                    cameraImageId = cameraPhoto?.takeIf { it.cardId == destination.cardId }?.imageId,
                    cameraOriginalImageId = cameraPhoto?.takeIf { it.cardId == destination.cardId }?.originalImageId,
                    cameraFrameStyleId = cameraPhoto?.takeIf { it.cardId == destination.cardId }?.frameStyleId,
                    cameraRequestVersion = cameraPhoto?.takeIf { it.cardId == destination.cardId }?.version ?: 0L)
                is Destination.CardCamera -> com.kigtts.next.ui.cards.camera.CardCameraScreen(
                    onCapture = { shot -> model.cardCapture.captured(destination, shot.original, shot.displayFile, shot.format) },
                    onCancel = model::navigateBack, message = model::message,
                    initialFormat = destination.cardId?.let { id -> model.cards.cards.value.firstOrNull { it.id == id } }
                        ?.let { model.stickers.frameStyle(it).format })
                is Destination.CardPreview -> com.kigtts.next.ui.cards.CardPreviewScreen(model.cards, model.stickers, destination.cardId,
                    { model.navigate(Destination.CardEditor(it)) }, model::message, onQuickImage = model::quickImageCard)
                Destination.Cards -> com.kigtts.next.ui.cards.CardsScreen(model.cards, model.stickers,
                    { model.navigate(Destination.CardPreview(it)) }, { model.navigate(Destination.CardEditor(it)) }, model::message,
                    onQuickImage = model::quickImageCard, openCamera = { model.cardCapture.open() })
                is Destination.StickerReview -> com.kigtts.next.ui.cards.StickerReviewScreen(model, destination.token)
                is Destination.StickerLibrary -> com.kigtts.next.ui.cards.StickerLibraryScreen(model.cards, model.stickers, model::message,
                    onQuickImage = model::quickImageCard, requestedLibrary = destination.libraryId)
                is Destination.PluginPage -> PluginPageScreen(model.registry.plugin(destination.pluginId)?.page(destination.pageId, model.context()), destination.pluginId in state.busyPlugins,
                    resolve = { resource -> model.pluginManager.paths.existingResource(resource.pluginId ?: destination.pluginId, resource.path) }) { action, inputs ->
                    model.invoke(destination.pluginId, action, inputs)
                }
            }
    }
}
