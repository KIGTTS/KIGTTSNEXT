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
import com.kigtts.next.ui.components.ConnectedChoiceGroup
import com.kigtts.next.ui.media.ThumbnailFramingDialog
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PhraseResourceSettings(draft: PhraseDraft, model: AppViewModel, enabled: Boolean, leave: () -> Unit) {
    val scope = rememberCoroutineScope(); var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var framing by remember { mutableStateOf(false) }
    fun import(uri: android.net.Uri?, type: QuickCardType) { if (uri == null) return
        scope.launch { importing = true; error = null
            try { model.content.importPhrase(draft.key, type, uri) }
            catch (e: Exception) { error = e.message ?: "导入失败" } finally { importing = false }
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { import(it, QuickCardType.MEDIA) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { import(it, QuickCardType.AUDIO) }
    val entry = model.mediaRepository.get(draft.mediaId)
    val state by model.state.collectAsStateWithLifecycle()
    if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    when (draft.type) {
        QuickCardType.TEXT -> MotionTextButton(feedbackClick { leave(); model.content.editor(target = "phrase:" + draft.key) }, enabled = enabled) { Text("打开文本编辑器") }
        QuickCardType.AUDIO -> MotionOutlinedButton(feedbackClick { audioPicker.launch(arrayOf("audio/*")) }, enabled = enabled && !importing,
            modifier = Modifier.fillMaxWidth().testTag("phrase-choose-audio")) { Text(if (entry == null) "选择音频" else "更换音频") }
        QuickCardType.MEDIA -> MotionOutlinedButton(feedbackClick { mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            enabled = enabled && !importing, modifier = Modifier.fillMaxWidth().testTag("phrase-choose-media")) { Text(if (entry == null) "选择图片或视频" else "更换图片或视频") }
        QuickCardType.PAGE -> Row {
            MotionTextButton(feedbackClick { leave(); model.content.editor(html = true, target = "phrase:" + draft.key) }, enabled = enabled,
                modifier = Modifier.testTag("phrase-edit-html")) { Text(if (entry == null) "编写 HTML" else "编辑 HTML") }
            MotionTextButton(feedbackClick { leave(); model.content.picker("phrase:" + draft.key) }, enabled = enabled,
                modifier = Modifier.testTag("phrase-choose-page")) { Text("选择页面") }
        }
    }
    MotionColumn(if(draft.type==QuickCardType.TEXT)emptyList() else listOfNotNull(entry),{ resource,_->resource.id },enabled=enabled) { resource,interactive->
        PhraseResourceInfo(resource,interactive,draft.type in setOf(QuickCardType.MEDIA,QuickCardType.PAGE),{ framing=true },
            { model.content.editPhrase { it.copy(mediaId=null,resources=it.resources-it.type) } })
        PhraseResourcePreview(resource,model.mediaRepository,interactive,state.appleLivePhotos,draft.audioMode,state.autoPlayback)
        if(resource.kind in setOf(MediaKind.AUDIO,MediaKind.VIDEO,MediaKind.MOTION_PHOTO))Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(when(resource.kind) { MediaKind.VIDEO->"自动播放视频";MediaKind.MOTION_PHOTO->"自动播放实况";else->"自动播放" },Modifier.weight(1f))
            Switch(draft.autoPlay,feedbackChange { value -> model.content.editPhrase { it.copy(autoPlay=value) } },enabled=interactive,
                colors=SwitchDefaults.colors().animateEnabled(interactive),
                modifier=Modifier.testTag("phrase-auto-play"))
        }
        if(resource.kind in setOf(MediaKind.VIDEO,MediaKind.MOTION_PHOTO)) {
            Text("音频播放",style=MaterialTheme.typography.labelLarge)
            ConnectedChoiceGroup("音频播放",MediaAudioMode.entries,draft.audioMode ?: MediaAudioMode.defaultFor(resource.kind),{ it.label },
                { mode->model.content.editPhrase { it.copy(audioMode=mode) } },Modifier.testTag("phrase-audio-mode"),enabled=interactive,compactLabels=true)
        }
    }
    if (framing && entry != null) ThumbnailFramingDialog(entry, model.mediaRepository) { framing = false }
}
