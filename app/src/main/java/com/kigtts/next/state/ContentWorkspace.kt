package com.kigtts.next.state

import android.content.Context
import com.kigtts.next.media.*
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import androidx.core.content.edit
import com.kigtts.next.text.*

data class ContentDraft(val token:String,val html:Boolean,val title:String,val initial:String,val target:String,val mediaId:String?=null,
    val value:String=initial,val currentTitle:String=title,val filePath:String?=null,val selection:Int=0,
    val formats:List<TextFormatRun> = emptyList(), val initialFormats:List<TextFormatRun> = formats) {
    val dirty get() = initial!=value || title!=currentTitle || formats!=initialFormats
}

/** Editing/import handoffs keep drafts alive while normal secondary pages enter and return. */
class ContentWorkspace(context:Context,val repository:MediaRepository,private val library:QuickTextLibrary,
    private val state:()->AppState,private val update:((AppState)->AppState)->Unit,private val navigate:(Destination)->Unit,
    private val back:()->Unit,private val setText:(String)->Unit,private val playAudio:(MediaEntry,Boolean)->Unit = { _,_ -> },
    private val stopAudio:()->Unit = {}, private val autoRead:()->Unit = {},
    private val editorIo:CoroutineDispatcher=Dispatchers.IO, private val permissions:com.kigtts.plugin.runtime.PluginPermissions?=null,
    private val openCustomTab: ((String, String) -> Unit)? = null) {
    private val history = ContentHistory()
    private val editorSnapshot=ContentEditorSnapshot(context)
    private val editorCompletion=EditorSaveCompletion()
    val editorSavePending=editorCompletion.pending;val editorSaveCompleted=editorCompletion.completed
    private val phraseCompletion=EditorSaveCompletion()
    val phraseSavePending=phraseCompletion.pending;val phraseSaveCompleted=phraseCompletion.completed
    private val preferences=context.getSharedPreferences("media-presentation",0)
    private val phraseState=MutableStateFlow<PhraseDraft?>(null);val phraseDraft=phraseState.asStateFlow()
    private val editorState=MutableStateFlow<ContentDraft?>(null);val editorDraft=editorState.asStateFlow()
    private val discardState=MutableStateFlow(false);val discardRequested=discardState.asStateFlow()
    fun editEditor(value:String,title:String,formats:List<TextFormatRun>?=null,selection:Int?=null) {
        if(editorSavePending.value)return
        editorState.value=editorState.value?.let { it.copy(value=value,currentTitle=title,
            formats = formats ?: TextFormatting.rebase(it.formats,it.value,value), selection=selection ?: it.selection) }
    }
    fun editorSelection(selection:Int) { editorState.value=editorState.value?.copy(selection=selection) }
    fun savedEditorDraft():String? = editorState.value?.let(editorSnapshot::write)
    fun restoreEditorDraft(token:String):Boolean {
        if(editorState.value!=null)return false
        editorState.value=editorSnapshot.read(token) ?: return false;return true
    }
    fun requestDiscard():Boolean { if(editorSavePending.value)return true;if(editorState.value?.dirty!=true) { discardState.value=false;editorState.value=null;return false };discardState.value=true;return true }
    fun keepEditor() { if(!editorSavePending.value)discardState.value=false }
    fun discardEditor() { if(editorSavePending.value)return;discardState.value=false;editorState.value=null;back() }
    fun show(entry:MediaEntry,autoPlay:Boolean=true,audioMode:MediaAudioMode?=null) {
        if(entry.kind!=MediaKind.AUDIO && state().mediaId==null)history.push(state())
        showCurrent(entry,autoPlay,audioMode)
    }
    private fun showCurrent(entry:MediaEntry,autoPlay:Boolean,audioMode:MediaAudioMode?) {
        stopAudio()
        if (entry.kind == MediaKind.AUDIO) {
            history.clear();resetSelection()
            update { it.copy(mediaId=null,audioId=null,editing=false,mediaEditing=false,quickTextEditing=false,
                webCanGoBack=false,contentAutoPlay=autoPlay,contentAudioMode=audioMode,contentRevision=it.contentRevision+1) }
            playAudio(entry,autoPlay&&state().autoPlayback);return
        }
        saveSelection(entry.id,autoPlay,audioMode)
        update { it.copy(mediaId=entry.id,webPermissionEntryId=null,editing=false,mediaEditing=false,quickTextEditing=false,sidePage=null,
            webCanGoBack=false,audioId=null,contentAutoPlay=autoPlay,contentAudioMode=audioMode,contentRevision=it.contentRevision+1,subtitleFormats=emptyList(),subtitleRevision=it.subtitleRevision+1) }
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
        if(media!=null)saveSelection(media.id,previous?.autoPlay ?: true,previous?.audioMode)
        update { it.copy(editorValue=previous?.text ?: it.editorValue, subtitleFormats=previous?.formats.orEmpty(),
            mediaId=media?.id,webPermissionEntryId=null,audioId=null,contentAutoPlay=previous?.autoPlay ?: true,contentAudioMode=previous?.audioMode,
            forcedFullscreen=previous?.fullscreen ?: false,editing=false,mediaEditing=false,webCanGoBack=false,
            subtitleRevision=it.subtitleRevision+1,contentRevision=it.contentRevision+1) }
        if(audio!=null)playAudio(audio,false)
    }
    fun clearHistory() = history.clear()
    fun send(phrase:QuickPhrase) {
        val entry = repository.get(phrase.mediaId)
        if (entry?.kind == MediaKind.WEB && phrase.metadata["web.openMode"] == "custom-tab" && openCustomTab != null) {
            openCustomTab.invoke(entry.primary, phrase.title); return
        }
        when (phrase.cardType(entry?.kind)) {
            QuickCardType.MEDIA, QuickCardType.PAGE -> entry?.let { show(it,phrase.autoPlay,phrase.audioMode); update { state -> state.copy(webPermissionEntryId=phrase.id) }; preferences.edit { putString("webEntry",phrase.id) } } ?: setText(phrase.text)
            QuickCardType.AUDIO -> { history.clear();setText(phrase.text);update { it.copy(contentAutoPlay=phrase.autoPlay,
                subtitleFormats=if(phrase.richText)phrase.formats else emptyList()) }
                if (entry?.kind == MediaKind.AUDIO) playAudio(entry,phrase.autoPlay&&state().autoPlayback) }
            QuickCardType.TEXT -> { history.clear();setText(phrase.text); if (phrase.richText) update { it.copy(subtitleFormats = phrase.formats) };if(state().autoPlayback)autoRead() }
        }
    }
    fun beginPhrase(phrase:QuickPhrase?,category:String) {
        if(phraseSavePending.value)return
        val type = phrase?.cardType(repository.get(phrase.mediaId)?.kind) ?: QuickCardType.TEXT
        phraseState.value=PhraseDraft(UUID.randomUUID().toString(),phrase?.id,phrase?.text.orEmpty(),phrase?.title.orEmpty(),phrase?.category ?: category,phrase?.mediaId,
            type,phrase?.colorMode ?: CardColorMode.DEFAULT,phrase?.color ?: 0xFF00696E,true,phrase?.formats.orEmpty(),
            mapOf(type to phrase?.mediaId),phrase?.autoPlay ?: true,phrase?.audioMode,phrase?.metadata.orEmpty())
    }
    fun editPhrase(change:(PhraseDraft)->PhraseDraft) { if(!phraseSavePending.value)phraseState.value=phraseState.value?.let(change) }
    fun cancelPhrase() { if(!phraseSavePending.value)phraseState.value=null }
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
    fun savePhrase():Boolean {
        if(phraseSavePending.value)return false
        val draft=phraseState.value ?: return false
        return persistPhrase(draft,false)
    }
    private fun persistPhrase(draft:PhraseDraft,retain:Boolean):Boolean {
        if(!draft.valid(repository))return false
        val id=draft.phraseId ?: UUID.randomUUID().toString()
        val saved=library.savePhrase(QuickPhrase(draft.text,draft.category,id,draft.title,
            if (draft.type == QuickCardType.TEXT) null else draft.mediaId,draft.type,draft.colorMode,draft.color,draft.richText,draft.formats,draft.autoPlay,draft.audioMode,draft.metadata))
        if(saved && draft.phraseId == null) {
            val before = com.kigtts.next.media.WebNetworkGate.owner(draft.key)
            val access = permissions?.access(before,"network")
            if(access != null && access != com.kigtts.plugin.runtime.PermissionAccess.ASK) permissions?.set(com.kigtts.next.media.WebNetworkGate.owner(id),"network",access)
            permissions?.clear(before)
        }
        if(saved)phraseState.value=if(retain)draft.copy(phraseId=id) else null
        return saved
    }
    fun savePhraseForExit(animated:Boolean):Boolean {
        val draft=phraseState.value ?: return false
        if(!phraseCompletion.begin(draft.key))return false
        try {
            if(!persistPhrase(draft,true)) {phraseCompletion.clear(draft.key);return false}
            phraseCompletion.acknowledge(draft.key,animated)
            if(!animated)cleanupPhraseSave(draft.key)
            return true
        } catch(failure:Exception) {phraseCompletion.clear(draft.key);throw failure}
    }
    fun cleanupPhraseSave(token:String):Boolean {
        if(!phraseCompletion.ready(token)||(state().destination as? Destination.PhraseEditor)?.token!=token)return false
        phraseCompletion.clear(token);if(phraseState.value?.key==token)phraseState.value=null
        back();return true
    }
    fun cleanupEditorSave(token:String):Boolean {
        if(!editorCompletion.ready(token)||(state().destination as? Destination.ContentEditor)?.token!=token)return false
        editorCompletion.clear(token);discardState.value=false;if(editorState.value?.token==token)editorState.value=null
        back();return true
    }
    /** Covered editors keep accepted saves; actual pop abandons UI completion without undoing saved data. */
    fun onDestinationChanged(destination:Destination) {
        val owners=state().navigationStack+destination
        editorCompletion.token?.let {token->
            if(owners.none {(it as? Destination.ContentEditor)?.token==token}) {
                editorCompletion.clear(token);discardState.value=false;if(editorState.value?.token==token)editorState.value=null
            } else if(editorCompletion.ready(token)&&!editorCompletion.animated)cleanupEditorSave(token)
        }
        phraseCompletion.token?.let {token->
            if(owners.none {(it as? Destination.PhraseEditor)?.token==token}) {
                phraseCompletion.clear(token);if(phraseState.value?.key==token)phraseState.value=null
            } else if(phraseCompletion.ready(token)&&!phraseCompletion.animated)cleanupPhraseSave(token)
        }
    }
    fun picker(target:String="display")=navigate(Destination.MediaLibrary(target))
    suspend fun htmlFiles(target:String="display",selectedMedia:String?=null) {
        val selected=selectedMedia ?: if(target.startsWith("phrase:"))phraseState.value?.mediaId else state().mediaId
        val entry=withContext(Dispatchers.IO) { repository.editableHtml(selected) }
        if(target.startsWith("phrase:"))editPhrase { it.attach(QuickCardType.PAGE,entry.id) }
        navigate(Destination.HtmlFiles(entry.id,target))
    }
    suspend fun editorFile(id:String,path:String,target:String) {
        if(editorSavePending.value)return
        val text=withContext(Dispatchers.IO) { repository.htmlFiles.read(id,path) }
        val token=UUID.randomUUID().toString()
        discardState.value=false
        editorState.value=ContentDraft(token,true,path.substringAfterLast('/'),text,target,id,filePath=path)
        navigate(Destination.ContentEditor(token))
    }
    fun choose(entry:MediaEntry,target:String) {
        if(target.startsWith("phrase:"))editPhrase { it.attach(it.type,entry.id) } else show(entry)
        back()
    }
    fun editor(html:Boolean=false,target:String="display",selectedMedia:String?=null) {
        if(editorSavePending.value)return
        val draft=phraseState.value;val media=repository.get(selectedMedia ?: if(target.startsWith("phrase:"))draft?.mediaId else state().mediaId)
        val initial=if(html) { if(media?.kind==MediaKind.HTML) repository.file(media)?.takeIf { it.length()<=2*1024*1024 }?.readText().orEmpty()
            else "<!doctype html>\n<html lang=\"zh\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><body><h1>你好</h1></body></html>" }
            else if(target.startsWith("phrase:"))draft?.text.orEmpty() else state().editorText
        val formats=if(html)emptyList() else if(target.startsWith("phrase:"))draft?.formats.orEmpty() else state().subtitleFormats
        val key=UUID.randomUUID().toString();discardState.value=false;editorState.value=ContentDraft(key,html,media?.name ?: if(html)"HTML" else "文本",initial,target,media?.id,formats=formats)
        navigate(Destination.ContentEditor(key))
    }
    suspend fun saveEditor(value:String,title:String,afterSaved:((cleanup:()->Unit)->Unit)?=null) {
        val draft=editorState.value ?: return
        if(!editorCompletion.begin(draft.token))return
        try {
        if(draft.html) {
            if(draft.filePath!=null) withContext(editorIo) { repository.htmlFiles.write(requireNotNull(draft.mediaId),draft.filePath,value) }
            else { val entry=withContext(editorIo) { repository.html(title,value,draft.mediaId?.takeIf { repository.get(it)?.kind==MediaKind.HTML&&repository.get(it)?.pluginId==null }) }
                if(draft.target.startsWith("phrase:"))editPhrase { it.attach(QuickCardType.PAGE,entry.id) } else show(entry) }
        } else {
            val formats=TextFormatting.rebase(draft.formats,draft.value,value)
            if(draft.target.startsWith("phrase:"))editPhrase { it.copy(text=value,richText=true,formats=formats) }
            else { setText(value);update { it.copy(subtitleFormats=formats) } }
        }
        // Retain the form and its confirmation window until a successful exit animation completes.
        val animated=discardState.value||afterSaved!=null
        if(!editorCompletion.acknowledge(draft.token,animated))return
        val cleanup:()->Unit={cleanupEditorSave(draft.token);Unit}
        if(afterSaved!=null)afterSaved(cleanup) else if(!animated)cleanup()
        } catch(failure:Exception) {editorCompletion.clear(draft.token);throw failure}
    }
    suspend fun present(owner:String,content:SubtitleContent) {
        if(content is SubtitleContent.Text) { setText(content.text);return }
        show(withContext(Dispatchers.IO) { repository.plugin(owner,content) })
    }
    private fun saveSelection(id:String,autoPlay:Boolean,audioMode:MediaAudioMode?) { preferences.edit {
        putString("selected",id);putBoolean("autoPlay",autoPlay);putString("audioMode",audioMode?.name)
    } }
    fun resetSelection() { preferences.edit { remove("selected");remove("autoPlay");remove("audioMode");remove("webEntry") } }
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
