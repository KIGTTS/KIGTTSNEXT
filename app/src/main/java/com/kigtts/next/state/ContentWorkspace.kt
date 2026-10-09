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

data class PhraseDraft(val key:String,val phraseId:String?,val text:String,val description:String,val category:String,val mediaId:String?)
data class ContentDraft(val token:String,val html:Boolean,val title:String,val initial:String,val target:String,val mediaId:String?=null,
    val value:String=initial,val currentTitle:String=title) { val dirty get() = initial!=value || title!=currentTitle }

/** Editing/import handoffs keep drafts alive while normal secondary pages enter and return. */
class ContentWorkspace(context:Context,val repository:MediaRepository,private val library:QuickTextLibrary,
    private val state:()->AppState,private val update:((AppState)->AppState)->Unit,private val navigate:(Destination)->Unit,
    private val back:()->Unit,private val setText:(String)->Unit) {
    private val preferences=context.getSharedPreferences("media-presentation",0)
    private val phraseState=MutableStateFlow<PhraseDraft?>(null);val phraseDraft=phraseState.asStateFlow()
    private val editorState=MutableStateFlow<ContentDraft?>(null);val editorDraft=editorState.asStateFlow()
    private val discardState=MutableStateFlow(false);val discardRequested=discardState.asStateFlow()
    fun editEditor(value:String,title:String) { editorState.value=editorState.value?.copy(value=value,currentTitle=title) }
    fun requestDiscard():Boolean { if(editorState.value?.dirty!=true)return false;discardState.value=true;return true }
    fun keepEditor() { discardState.value=false }
    fun discardEditor() { discardState.value=false;editorState.value=null;back() }
    fun show(entry:MediaEntry) {
        preferences.edit { putString("selected",entry.id) }
        update { it.copy(mediaId=entry.id,editing=false,mediaEditing=false,quickTextEditing=false,sidePage=null,
            webCanGoBack=false,subtitleRevision=it.subtitleRevision+1) }
    }
    fun send(phrase:QuickPhrase) { repository.get(phrase.mediaId)?.let(::show) ?: setText(phrase.text) }
    fun beginPhrase(phrase:QuickPhrase?,category:String) { phraseState.value=PhraseDraft(UUID.randomUUID().toString(),phrase?.id,phrase?.text.orEmpty(),phrase?.description.orEmpty(),phrase?.category ?: category,phrase?.mediaId) }
    fun editPhrase(change:(PhraseDraft)->PhraseDraft) { phraseState.value=phraseState.value?.let(change) }
    fun cancelPhrase() { phraseState.value=null }
    fun savePhrase() { phraseState.value?.let { draft ->
        if(draft.phraseId==null)library.add(draft.text,draft.category,draft.description,draft.mediaId)
        else library.edit(draft.phraseId,draft.text,draft.description,draft.category,draft.mediaId)
    };phraseState.value=null }
    fun picker(target:String="display")=navigate(Destination.MediaLibrary(target))
    fun choose(entry:MediaEntry,target:String) {
        if(target.startsWith("phrase:"))editPhrase { it.copy(mediaId=entry.id,text=it.text.ifBlank { entry.name }) } else show(entry)
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
            if(draft.target.startsWith("phrase:"))editPhrase { it.copy(mediaId=entry.id,text=it.text.ifBlank { entry.name }) } else show(entry)
        } else if(draft.target.startsWith("phrase:"))editPhrase { it.copy(text=value) } else setText(value)
        editorState.value=null;back()
    }
    suspend fun present(owner:String,content:SubtitleContent) {
        if(content is SubtitleContent.Text) { setText(content.text);return }
        show(withContext(Dispatchers.IO) { repository.plugin(owner,content) })
    }
    fun resetSelection() { preferences.edit { remove("selected") } }
    fun remove(id:String) { library.detachMedia(id);repository.remove(id);if(state().mediaId==id)setText(state().editorText) }
}
