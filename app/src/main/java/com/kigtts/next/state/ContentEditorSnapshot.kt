package com.kigtts.next.state

import android.content.Context
import org.json.JSONObject
import java.io.File
import com.kigtts.next.text.*

/** Large source drafts stay in private cache; saved-instance state contains only the token. */
internal class ContentEditorSnapshot(context:Context) {
    private val root=File(context.cacheDir,"code-editor-drafts").apply { mkdirs() }
    fun write(draft:ContentDraft):String {
        require(draft.token.matches(Regex("[a-f0-9-]{36}")))
        val target=File(root,draft.token+".json");val stage=File(root,".save.tmp")
        stage.writeText(JSONObject().put("token",draft.token).put("html",draft.html).put("title",draft.title)
            .put("initial",draft.initial).put("target",draft.target).put("media",draft.mediaId).put("value",draft.value)
            .put("currentTitle",draft.currentTitle).put("file",draft.filePath).put("selection",draft.selection)
            .put("formats",writeTextFormats(draft.formats,draft.value.length))
            .put("initialFormats",writeTextFormats(draft.initialFormats,draft.initial.length)).toString(),Charsets.UTF_8)
        java.nio.file.Files.move(stage.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        root.listFiles().orEmpty().filter { it!=target }.sortedByDescending { it.lastModified() }.drop(7).forEach { it.delete() }
        return draft.token
    }
    fun read(token:String):ContentDraft? = runCatching {
        require(token.matches(Regex("[a-f0-9-]{36}")))
        val f=File(root,"$token.json");require(f.length() in 1..(16L*1024*1024))
        val j=JSONObject(f.readText(Charsets.UTF_8))
        ContentDraft(token,j.getBoolean("html"),j.getString("title"),j.getString("initial"),j.getString("target"),
            j.optString("media").takeIf { !j.isNull("media") },j.getString("value"),j.getString("currentTitle"),
            j.optString("file").takeIf { !j.isNull("file") },j.optInt("selection"),
            readTextFormats(j.optJSONArray("formats"),j.getString("value").length),
            readTextFormats(j.optJSONArray("initialFormats"),j.getString("initial").length))
    }.getOrNull()
}
