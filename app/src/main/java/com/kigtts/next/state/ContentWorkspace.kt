package com.kigtts.next.state

import android.content.Context
import com.kigtts.next.media.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import androidx.core.content.edit

data class ContentDraft(val token:String,val html:Boolean,val title:String,val initial:String,val target:String,val mediaId:String?=null,
    val value:String=initial,val currentTitle:String=title) { val dirty get() = initial!=value || title!=currentTitle }

/** Editing/import handoffs keep drafts alive while normal secondary pages enter and return. */
class ContentWorkspace(context:Context,val repository:MediaRepository,private val library:QuickTextLibrary,
    private val state:()->AppState,private val update:((AppState)->AppState)->Unit,private val navigate:(Destination)->Unit,
    private val back:()->Unit,private val setText:(String)->Unit,private val playAudio:(MediaEntry,Boolean)->Unit = { _,_ -> },
    private val stopAudio:()->Unit = {}, private val autoRead:()->Unit = {}) {
    private val history = ContentHistory()
    private val preferences=context.getSharedPreferences("media-presentation",0)
    private val phraseState=MutableStateFlow<PhraseDraft?>(null);val phraseDraft=phraseState.asStateFlow()
    private val editorState=MutableStateFlow<ContentDraft?>(null);val editorDraft=editorState.asStateFlow()
    private val discardState=MutableStateFlow(false);val discardRequested=discardState.asStateFlow()
    fun editEditor(value:String,title:String) { editorState.value=editorState.value?.copy(value=value,currentTitle=title) }
    fun requestDiscard():Boolean { if(editorState.value?.dirty!=true)return false;discardState.value=true;return true }
    fun keepEditor() { discardState.value=false }
    fun discardEditor() { discardState.value=false;editorState.value=null;back() }
    fun show(entry:MediaEntry,autoPlay:Boolean=true) {
        if(entry.kind!=MediaKind.AUDIO)history.push(state())
        showCurrent(entry,autoPlay)
    }
    private fun showCurrent(entry:MediaEntry,autoPlay:Boolean) {
        stopAudio()
        if (entry.kind == MediaKind.AUDIO) {
            history.clear();resetSelection()
            update { it.copy(mediaId=null,audioId=null,editing=false,mediaEditing=false,quickTextEditing=false,
                webCanGoBack=false,contentAutoPlay=autoPlay,contentRevision=it.contentRevision+1) }
            playAudio(entry,autoPlay&&state().autoPlayback);return
        }
        preferences.edit { putString("selected",entry.id) }
        update { it.copy(mediaId=entry.id,editing=false,mediaEditing=false,quickTextEditing=false,sidePage=null,
            webCanGoBack=false,audioId=null,contentAutoPlay=autoPlay,contentRevision=it.contentRevision+1,subtitleFormats=emptyList(),subtitleRevision=it.subtitleRevision+1) }
    }
    fun closeContent() {
        val current=state(); if(current.mediaId==null&&current.audioId==null)return
        stopAudio()
        if(current.mediaId==null&&current.audioId!=null) {
            history.clear();update { it.copy(audioId=null) };return
        }
        val previous=history.pop()
        resetSelection()
        val media=repository.get(previous?.media); val audio=repository.get(previous?.audio)
        if(media!=null)preferences.edit { putString("selected",media.id) }
        update { it.copy(editorValue=previous?.text ?: it.editorValue, subtitleFormats=previous?.formats.orEmpty(),
            mediaId=media?.id,audioId=null,contentAutoPlay=previous?.autoPlay ?: true,
            forcedFullscreen=previous?.fullscreen ?: false,editing=false,mediaEditing=false,webCanGoBack=false,
            subtitleRevision=it.subtitleRevision+1,contentRevision=it.contentRevision+1) }
        if(audio!=null)playAudio(audio,false)
    }
    fun clearHistory() = history.clear()
    fun send(phrase:QuickPhrase) {
        val entry = repository.get(phrase.mediaId)
        when (phrase.cardType(entry?.kind)) {
            QuickCardType.MEDIA, QuickCardType.PAGE -> entry?.let { show(it,phrase.autoPlay) } ?: setText(phrase.text)
            QuickCardType.AUDIO -> { history.clear();setText(phrase.text);update { it.copy(contentAutoPlay=phrase.autoPlay) }
                if (entry?.kind == MediaKind.AUDIO) playAudio(entry,phrase.autoPlay&&state().autoPlayback) }
            QuickCardType.TEXT -> { history.clear();setText(phrase.text); if (phrase.richText) update { it.copy(subtitleFormats = phrase.formats) };if(state().autoPlayback)autoRead() }
        }
    }
    fun beginPhrase(phrase:QuickPhrase?,category:String) {
        val type = phrase?.cardType(repository.get(phrase.mediaId)?.kind) ?: QuickCardType.TEXT
        phraseState.value=PhraseDraft(UUID.randomUUID().toString(),phrase?.id,phrase?.text.orEmpty(),phrase?.title.orEmpty(),phrase?.category ?: category,phrase?.mediaId,
            type,phrase?.colorMode ?: CardColorMode.DEFAULT,phrase?.color ?: 0xFF00696E,phrase?.richText ?: false,phrase?.formats.orEmpty(),
            mapOf(type to phrase?.mediaId),phrase?.autoPlay ?: true)
    }
    fun editPhrase(change:(PhraseDraft)->PhraseDraft) { phraseState.value=phraseState.value?.let(change) }
    fun cancelPhrase() { phraseState.value=null }
    fun savedPhraseDraft(): String? = phraseState.value?.let(PhraseDraftState::write)
    fun restorePhraseDraft(value: String): Boolean {
        if(phraseState.value!=null)return false
        val draft=PhraseDraftState.read(value) ?: return false
        phraseState.value=draft;return true
    }
    suspend fun importPhrase(key: String, type: QuickCardType, uri: android.net.Uri) {
        val entry = withContext(Dispatchers.IO) { repository.import(uri) }
        val accepted = when (type) {
            QuickCardType.AUDIO -> entry.kind == MediaKind.AUDIO
            QuickCardType.MEDIA -> entry.kind in setOf(MediaKind.IMAGE, MediaKind.MOTION_PHOTO, MediaKind.VIDEO)
            else -> false
        }
        if (!accepted || phraseState.value?.key != key) {
            withContext(Dispatchers.IO) { repository.remove(entry.id) }
            require(accepted) { if (type == QuickCardType.AUDIO) "请选择音频文件" else "请选择图片或视频" }; return
        }
        editPhrase { it.attach(type, entry.id) }
    }
    fun savePhrase() { phraseState.value?.let { draft ->
        if (draft.valid(repository)) library.savePhrase(QuickPhrase(draft.text,draft.category,draft.phraseId ?: UUID.randomUUID().toString(),draft.title,
            if (draft.type == QuickCardType.TEXT) null else draft.mediaId,draft.type,draft.colorMode,draft.color,draft.richText,draft.formats,draft.autoPlay))
    };phraseState.value=null }
    fun picker(target:String="display")=navigate(Destination.MediaLibrary(target))
    fun choose(entry:MediaEntry,target:String) {
        if(target.startsWith("phrase:"))editPhrase { it.attach(it.type,entry.id) } else show(entry)
        back()
    }
    fun editor(html:Boolean=false,target:String="display",selectedMedia:String?=null) {
        val draft=phraseState.value;val media=repository.get(selectedMedia ?: if(target.startsWith("phrase:"))draft?.mediaId else state().mediaId)
        val initial=if(html) { if(media?.kind==MediaKind.HTML) repository.file(media)?.takeIf { it.length()<=2*1024*1024 }?.readText().orEmpty()
            else "<!doctype html>\n<html lang=\"zh\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><body><h1>你好</h1></body></html>" }
            else if(target.startsWith("phrase:"))draft?.text.orEmpty() else state().editorText
        val key=UUID.randomUUID().toString();editorState.value=ContentDraft(key,html,media?.name ?: if(html)"HTML" else "文本",initial,target,media?.id)
        navigate(Destination.ContentEditor(key))
    }
    suspend fun saveEditor(value:String,title:String) {
        val draft=editorState.value ?: return
        if(draft.html) { val entry=withContext(Dispatchers.IO) { repository.html(title,value,draft.mediaId?.takeIf { repository.get(it)?.kind==MediaKind.HTML&&repository.get(it)?.pluginId==null }) }
            if(draft.target.startsWith("phrase:"))editPhrase { it.attach(QuickCardType.PAGE,entry.id) } else show(entry)
        } else if(draft.target.startsWith("phrase:"))editPhrase { it.copy(text=value,formats=com.kigtts.next.text.TextFormatting.rebase(it.formats,it.text,value)) } else setText(value)
        editorState.value=null;back()
    }
    suspend fun present(owner:String,content:SubtitleContent) {
        if(content is SubtitleContent.Text) { setText(content.text);return }
        show(withContext(Dispatchers.IO) { repository.plugin(owner,content) })
    }
    fun resetSelection() { preferences.edit { remove("selected") } }
    private fun detach(id:String) {
        history.remove(id);library.detachMedia(id)
        if(state().mediaId==id || state().audioId==id)closeContent()
    }
    fun remove(id:String) { detach(id);repository.remove(id) }
    /** Player and UI handoff belong to main; persistent resource cleanup stays off the UI thread. */
    suspend fun removeAsync(id:String) {
        withContext(Dispatchers.Main) { detach(id) }
        withContext(Dispatchers.IO) { repository.remove(id) }
    }
}
