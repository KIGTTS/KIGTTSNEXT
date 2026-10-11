package com.kigtts.next.state

import com.kigtts.next.text.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal object QuickPhraseCodec {
    fun read(json: JSONObject): QuickPhrase {
        val text = json.getString("text")
        val formats = json.optJSONArray("formats")?.let { array -> (0 until minOf(array.length(), 256)).mapNotNull { i ->
            runCatching { val run = array.getJSONObject(i)
                TextFormatRun(run.getInt("start"), run.getInt("end"), TextFormat.valueOf(run.getString("kind")), run.optLong("value"))
            }.getOrNull()
        } }.orEmpty()
        return QuickPhrase(text, json.getString("category"), json.optString("id").takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            (if (json.has("title") && !json.isNull("title")) json.optString("title") else json.optString("description")).trim(),
            json.optString("mediaId").takeIf(String::isNotBlank), QuickCardType.entries.firstOrNull { it.name == json.optString("type") },
            CardColorMode.entries.firstOrNull { it.name == json.optString("colorMode") } ?: CardColorMode.DEFAULT,
            0xFF000000L or (json.optLong("color", 0xFF00696E) and 0xFFFFFF), json.optBoolean("richText"),
            TextFormatting.normalize(formats, text.length), json.optBoolean("autoPlay", true),
            com.kigtts.next.media.MediaAudioMode.read(json.optString("audioMode")))
    }
    fun write(item: QuickPhrase) = JSONObject().put("id", item.id).put("text", item.text).put("category", item.category)
        .put("title", item.title).put("mediaId", item.mediaId).put("type", item.type?.name).put("colorMode", item.colorMode.name)
        .put("color", item.color).put("richText", item.richText).put("autoPlay", item.autoPlay).put("audioMode", item.audioMode?.name).put("formats", JSONArray().also { array ->
            TextFormatting.normalize(item.formats, item.text.length).forEach { array.put(JSONObject().put("start", it.start)
                .put("end", it.end).put("kind", it.kind.name).put("value", it.value)) }
        })
}
