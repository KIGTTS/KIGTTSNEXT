package com.kigtts.next.ui.home

import com.kigtts.next.i18n.uiText

import com.kigtts.next.ui.components.animateEnabled

import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.MotionOutlinedButton
import com.kigtts.next.ui.components.MotionColumn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import com.kigtts.next.ui.media.VisualMediaPicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.components.ChoiceButtonGroup
import com.kigtts.next.ui.media.ThumbnailFramingDialog
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.ui.settings.SettingPosition
import com.kigtts.next.ui.cards.StickerPickerSheet
import kotlinx.coroutines.CancellationException

@Composable
internal fun PhraseResourceSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean, leave: () -> Unit) {
    val scope = rememberCoroutineScope(); var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var framing by remember { mutableStateOf(false) }
    var selectMain by remember { mutableStateOf(false) }
    var selectSticker by remember { mutableStateOf(false) }
    val importFailed = uiText("photo.sticker_import_failed")
    fun import(uri: android.net.Uri?, type: QuickCardType, mime: String? = null) { if (uri == null) return
        scope.launch { importing = true; error = null
            try { model.content.importPhrase(draft.key, type, uri, mime) }
            catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { error = e.message ?: importFailed } finally { importing = false }
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(VisualMediaPicker()) { import(it, QuickCardType.MEDIA) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it, QuickCardType.AUDIO) }
    val entries by model.mediaRepository.entries.collectAsStateWithLifecycle()
    val entry = entries.firstOrNull { it.id == draft.mediaId }
    val state by model.state.collectAsStateWithLifecycle()
    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (draft.type != QuickCardType.TEXT) PhraseEditorRow(if (draft.type == QuickCardType.PAGE || entry != null) SettingPosition.MIDDLE else SettingPosition.LAST) {
      when (draft.type) {
        QuickCardType.TEXT -> Unit
        QuickCardType.AUDIO -> MotionOutlinedButton(feedbackClick { audioPicker.launch(arrayOf("audio/*")) }, enabled = enabled && !importing,
            modifier = Modifier.fillMaxWidth().testTag("phrase-choose-audio")) { Text(if (entry == null) uiText("quick.pick_audio") else uiText("quick.change_audio")) }
        QuickCardType.MEDIA -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionOutlinedButton(feedbackClick { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                enabled = enabled && !importing, modifier = Modifier.fillMaxWidth().testTag("phrase-choose-media")) {
                Text(if (entry == null) uiText("quick.pick_media") else uiText("quick.change_media"))
            }
            MotionOutlinedButton(feedbackClick { selectSticker = true }, enabled = enabled && !importing,
                modifier = Modifier.fillMaxWidth().testTag("phrase-choose-sticker")) { Text(uiText("photo.choose_sticker")) }
        }
        QuickCardType.PAGE -> Row {
            MotionTextButton(feedbackClick { leave(); scope.launch { runCatching { model.content.htmlFiles("phrase:" + draft.key) }.onFailure { error=it.message } } }, enabled = enabled,
                modifier = Modifier.testTag("phrase-edit-html")) { Text(if (entry == null) uiText("quick.create_page_files") else uiText("quick.manage_page_files")) }
            MotionTextButton(feedbackClick { selectMain = true }, enabled = enabled && entry?.kind == MediaKind.HTML && entry.pluginId == null,
                modifier = Modifier.testTag("phrase-choose-page")) { Text(uiText("quick.main_page")) }
        }
    }
    }
    if (draft.type == QuickCardType.PAGE) {
        PhraseEditorRow(SettingPosition.MIDDLE) { PhraseIconSettings(draft, model, enabled) }
        PhraseEditorRow(if (entry == null) SettingPosition.LAST else SettingPosition.MIDDLE) { PhraseNetworkSettings(draft, model, enabled) }
    }
    MotionColumn(if(draft.type==QuickCardType.TEXT)emptyList() else listOfNotNull(entry),{ resource,_->resource.id },enabled=enabled) { resource,interactive->
      PhraseEditorRow(SettingPosition.LAST) {
        PhraseResourceInfo(resource,interactive,draft.type in setOf(QuickCardType.MEDIA,QuickCardType.PAGE),{ framing=true },
            { model.content.editPhrase { it.copy(mediaId=null,resources=it.resources-it.type) } })
        PhraseResourcePreview(resource,model.mediaRepository,interactive,state.appleLivePhotos,draft.audioMode,state.autoPlayback)
        if(resource.kind in setOf(MediaKind.AUDIO,MediaKind.VIDEO,MediaKind.MOTION_PHOTO))Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(when(resource.kind) { MediaKind.VIDEO->uiText("quick.autoplay_video");MediaKind.MOTION_PHOTO->uiText("quick.autoplay_motion");else->uiText("quick.autoplay") },Modifier.weight(1f))
            Switch(draft.autoPlay,feedbackChange { value -> model.content.editPhrase { it.copy(autoPlay=value) } },enabled=interactive,
                colors=SwitchDefaults.colors().animateEnabled(interactive),
                modifier=Modifier.testTag("phrase-auto-play"))
        }
      }
        if(resource.kind in setOf(MediaKind.VIDEO,MediaKind.MOTION_PHOTO)) {
            Text(uiText("quick.play_audio"),style=MaterialTheme.typography.labelLarge)
            ChoiceButtonGroup("音频播放",MediaAudioMode.entries,draft.audioMode ?: MediaAudioMode.defaultFor(resource.kind),{ it.label },
                { mode->model.content.editPhrase { it.copy(audioMode=mode) } },Modifier.testTag("phrase-audio-mode"),enabled=interactive,compactLabels=true)
        }
    }
    if (framing && entry != null) ThumbnailFramingDialog(entry, model.mediaRepository) { framing = false }
    if (selectMain && entry?.kind == MediaKind.HTML) PhraseMainPagePicker(model, entry.id) { selectMain = false }
    if (selectSticker && draft.type == QuickCardType.MEDIA) StickerPickerSheet(model.stickers, { selectSticker = false }) { item ->
        selectSticker = false
        val image = model.stickers.snapshot.value.images.firstOrNull { it.id == item.imageId }
        val file = image?.let { model.stickers.imageFile(it.id) }
        if (file == null) error = importFailed
        else import(android.net.Uri.fromFile(file), QuickCardType.MEDIA, image.mime)
    }
}
