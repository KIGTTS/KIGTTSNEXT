package com.kigtts.next.ui.home

import com.kigtts.next.ui.components.animateEnabled

import com.kigtts.next.ui.components.MotionTextButton
import com.kigtts.next.ui.components.MotionOutlinedButton
import com.kigtts.next.ui.components.MotionColumn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kigtts.next.media.*
import com.kigtts.next.state.*
import com.kigtts.next.ui.components.feedbackClick
import com.kigtts.next.ui.components.feedbackChange
import com.kigtts.next.ui.components.ChoiceButtonGroup
import com.kigtts.next.ui.media.ThumbnailFramingDialog
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kigtts.next.ui.settings.SettingPosition

@Composable
internal fun PhraseResourceSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean, leave: () -> Unit) {
    val scope = rememberCoroutineScope(); var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var framing by remember { mutableStateOf(false) }
    var selectMain by remember { mutableStateOf(false) }
    fun import(uri: android.net.Uri?, type: QuickCardType) { if (uri == null) return
        scope.launch { importing = true; error = null
            try { model.content.importPhrase(draft.key, type, uri) }
            catch (e: Exception) { error = e.message ?: "导入失败" } finally { importing = false }
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { import(it, QuickCardType.MEDIA) }
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
            modifier = Modifier.fillMaxWidth().testTag("phrase-choose-audio")) { Text(if (entry == null) "选择音频" else "更换音频") }
        QuickCardType.MEDIA -> MotionOutlinedButton(feedbackClick { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            enabled = enabled && !importing, modifier = Modifier.fillMaxWidth().testTag("phrase-choose-media")) { Text(if (entry == null) "选择图片或视频" else "更换图片或视频") }
        QuickCardType.PAGE -> Row {
            MotionTextButton(feedbackClick { leave(); scope.launch { runCatching { model.content.htmlFiles("phrase:" + draft.key) }.onFailure { error=it.message } } }, enabled = enabled,
                modifier = Modifier.testTag("phrase-edit-html")) { Text(if (entry == null) "创建页面文件" else "管理页面文件") }
            MotionTextButton(feedbackClick { selectMain = true }, enabled = enabled && entry?.kind == MediaKind.HTML && entry.pluginId == null,
                modifier = Modifier.testTag("phrase-choose-page")) { Text("选择主页面") }
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
            Text(when(resource.kind) { MediaKind.VIDEO->"自动播放视频";MediaKind.MOTION_PHOTO->"自动播放实况";else->"自动播放" },Modifier.weight(1f))
            Switch(draft.autoPlay,feedbackChange { value -> model.content.editPhrase { it.copy(autoPlay=value) } },enabled=interactive,
                colors=SwitchDefaults.colors().animateEnabled(interactive),
                modifier=Modifier.testTag("phrase-auto-play"))
        }
      }
        if(resource.kind in setOf(MediaKind.VIDEO,MediaKind.MOTION_PHOTO)) {
            Text("音频播放",style=MaterialTheme.typography.labelLarge)
            ChoiceButtonGroup("音频播放",MediaAudioMode.entries,draft.audioMode ?: MediaAudioMode.defaultFor(resource.kind),{ it.label },
                { mode->model.content.editPhrase { it.copy(audioMode=mode) } },Modifier.testTag("phrase-audio-mode"),enabled=interactive,compactLabels=true)
        }
    }
    if (framing && entry != null) ThumbnailFramingDialog(entry, model.mediaRepository) { framing = false }
    if (selectMain && entry?.kind == MediaKind.HTML) PhraseMainPagePicker(model, entry.id) { selectMain = false }
}
