package com.kigtts.next.state

import org.json.JSONObject

/** Small saved-instance snapshot for external system pickers, including "Don't keep activities". */
internal object PhraseDraftState {
    fun write(draft: PhraseDraft): String {
        val phrase=QuickPhrase(draft.text,draft.category,draft.phraseId ?: "",draft.title,draft.mediaId,
            draft.type,draft.colorMode,draft.color,draft.richText,draft.formats,draft.autoPlay,draft.audioMode)
        return QuickPhraseCodec.write(phrase).put("key",draft.key).put("phraseId",draft.phraseId)
            .put("resources",JSONObject().also { json -> draft.resources.forEach { (type,id) -> json.put(type.name,id) } }).toString()
    }
    fun read(value: String): PhraseDraft? = runCatching {
        require(value.toByteArray(Charsets.UTF_8).size<=512*1024)
        val json=JSONObject(value);val phrase=QuickPhraseCodec.read(json)
        val type=phrase.type ?: QuickCardType.TEXT
        val resources=json.optJSONObject("resources")
        PhraseDraft(json.getString("key"),json.optString("phraseId").takeIf { it.isNotEmpty() },phrase.text,phrase.title,
            phrase.category,phrase.mediaId,type,phrase.colorMode,phrase.color,phrase.richText,phrase.formats,
            QuickCardType.entries.mapNotNull { card -> resources?.optString(card.name)?.takeIf { it.isNotEmpty()&&it!="null" }?.let { card to it } }.toMap(),phrase.autoPlay,phrase.audioMode)
    }.getOrNull()
}
