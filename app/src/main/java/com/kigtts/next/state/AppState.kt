package com.kigtts.next.state

import com.kigtts.next.theme.ThemeSettings
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

enum class HomePanel(val label: String) { QUICK_TEXT("快捷文本") }
enum class SidePage(val label: String) { SKETCH("画板"), CARDS("拍立得") }
sealed interface Destination {
    data object Home : Destination
    data object Settings : Destination
    data object PersonalCards : Destination
    data object ContactFavorites : Destination
    data object Fonts : Destination
    data class SettingsDetail(val category: SettingsCategory) : Destination
    data object Plugins : Destination
    data class PluginPage(val pluginId: String, val pageId: String) : Destination
    data class MediaLibrary(val target: String = "display") : Destination
    data class HtmlFiles(val mediaId: String, val target: String = "display") : Destination
    data class HtmlPreview(val mediaId: String) : Destination
    data class QuickTextReview(val token:String):Destination
    data class ContentEditor(val token: String) : Destination
    data class PhraseEditor(val token: String, val adding: Boolean = false) : Destination
    data class CardEditor(val cardId: String) : Destination
    data class CardPreview(val cardId: String) : Destination
    data object Cards : Destination
    data class CardCamera(val requestId: String, val cardId: String? = null, val systemCamera: Boolean = false) : Destination
    data class StickerReview(val token: String) : Destination
    data class StickerLibrary(val libraryId: String? = null) : Destination
}
data class AppState(
    val destination: Destination = Destination.Home,
    val navigationStack: List<Destination> = emptyList(),
    val captionsVisible: Boolean = false,
    val captionHeight: Float = 128f,
    val panel: HomePanel? = null,
    val sidePage: SidePage? = null,
    val panelHeight: Float = 184f,
    val lastExpandedPanelHeight: Float = 184f,
    val panelColumns: Int = 1,
    val editing: Boolean = false,
    val forcedFullscreen: Boolean = false,
    val quickTextEditing: Boolean = false,
    val subtitleRevision: Int = 0,
    val pluginRevision: Int = 0,
    val busyPlugins: Set<String> = emptySet(),
    val editorValue: TextFieldValue = TextFieldValue("", TextRange.Zero),
    val subtitlePlaceholder: String = "点击输入文字",
    val transcript: String = "",
    val listening: Boolean = false,
    val voiceInputActive: Boolean = false,
    val continuousRecognition: Boolean = false,
    val voiceInputText: String = "",
    val recognitionOutput: com.kigtts.plugin.api.CaptionOutput = com.kigtts.plugin.api.CaptionOutput.PANEL,
    val speaking: Boolean = false,
    val streaming: Boolean = true,
    val subtitleSizePercent: Float = SubtitleSize.DEFAULT_PERCENT,
    val subtitleAppearance: SubtitleAppearance = SubtitleAppearance(),
    val captionTextSize: Float = 14f,
    val subtitleFlipped: Boolean = false,
    val selectedCategory: String = "常用",
    val theme: ThemeSettings = ThemeSettings(),
    val hapticsEnabled: Boolean = true,
    val mediaId: String? = null,
    val webPermissionEntryId: String? = null,
    val mediaEditing: Boolean = false,
    val webCanGoBack: Boolean = false,
    val webBackRevision: Int = 0,
    val appleLivePhotos: Boolean = false,
    val subtitleFormats: List<com.kigtts.next.text.TextFormatRun> = emptyList(),
    val audioId: String? = null,
    val autoPlayback: Boolean = false,
    val contentAutoPlay: Boolean = true,
    val contentAudioMode: com.kigtts.next.media.MediaAudioMode? = null,
    val contentRevision: Int = 0,
) {
    val editorText: String get() = editorValue.text
    val subtitleExpanded: Boolean get() = editing || forcedFullscreen || mediaEditing
}
