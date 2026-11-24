package com.kigtts.next.legacy

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class LegacyPhrase(val id: String, val text: String, val color: Long? = null,
    val audio: File? = null, val metadata: Map<String, String> = emptyMap())
internal data class LegacyGroup(val id: String, val title: String, val icon: String, val items: List<LegacyPhrase>)
internal data class LegacyCard(val id: String, val title: String, val note: String, val color: Long,
    val image: File?, val landscape: Boolean, val qr: String)
internal data class LegacyContent(val format: String, val groups: List<LegacyGroup> = emptyList(),
    val cards: List<LegacyCard> = emptyList(), val warnings: List<String> = emptyList(), val cardPackage: Boolean = false,
    val preferences: Map<String, Any> = emptyMap(), val resourceRoot: File? = null,
    val components: Set<String> = emptySet()) {
    fun validate(): LegacyContent {
        val items = groups.flatMap { it.items }
        require(groups.size <= 128 && items.size <= 10000 && cards.size <= 256) { "内容过多，请分批导入" }
        require(groups.map { it.id }.distinct().size == groups.size && items.map { it.id }.distinct().size == items.size &&
            cards.map { it.id }.distinct().size == cards.size) { "备份包含重复标识，请重新导出" }
        return this
    }
}

internal fun legacyId(value: String) = UUID.nameUUIDFromBytes(("original-kigtts/" + value).toByteArray(Charsets.UTF_8)).toString()
internal fun legacyColor(value: Any?): Long? = runCatching {
    when (value) {
        is Number -> value.toLong() and 0xffffffffL
        is String -> android.graphics.Color.parseColor(value).toLong() and 0xffffffffL
        else -> null
    }
}.getOrNull()

/** Decode original export metadata without running original code or applying its settings. */
internal object LegacyContentDecoder {
    fun decode(root: JSONObject, preferences: JSONObject? = null, resource: (String) -> File?): LegacyContent {
        val type = root.optString("type")
        require(root.optInt("version", 1) in 1..if (type == "kigtts_config_backup") 2 else 1) { "不支持的旧版备份版本" }
        return when (type) {
            "quick_subtitle" -> LegacyContent("旧版快捷文本包", groups(root, false, resource))
            "soundboard" -> LegacyContent("旧版音效包", groups(root, true, resource))
            "kigtts_quick_cards" -> LegacyContent("旧版名片包", cards = cards(root, resource), cardPackage = true)
            "kigtts_config_backup" -> {
                val values = decodeLegacyPreferences(requireNotNull(preferences) { "备份缺少 preferences.json" })
                val entries = values.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
                val components = root.optJSONArray("components")
                require(components == null || components.length() <= 32)
                configs(entries, recoverMissing = true, resource = resource).copy(format = "旧版数据备份包", preferences = values,
                    components = components?.let { array -> (0 until array.length()).map(array::getString).toSet() }.orEmpty())
            }
            else -> error("无法识别此格式，请选择旧版 .kigtpk、.kigspk、.kigcard 或 .kigconfig 文件")
        }.validate()
    }

    fun configs(values: Map<String, String>, recoverMissing: Boolean = false, resource: (String) -> File?): LegacyContent {
        fun json(name: String) = values[name]?.takeIf { it.isNotBlank() }?.let(::legacyJson)
        val warnings = mutableListOf<String>()
        val audio = { path: String ->
            resource(path) ?: if (!recoverMissing) null else {
                if (warnings.isEmpty()) warnings += "backup.legacy_missing_media"
                null
            }
        }
        return LegacyContent("旧版应用数据",
            json("quick_subtitle_config")?.let { groups(it, false, resource) }.orEmpty() +
                json("soundboard_config")?.let { groups(it, true, audio, recoverMissing) }.orEmpty(),
            json("quick_card_config")?.let { cards(it, audio, recoverMissing) }.orEmpty(), warnings).validate()
    }

    private fun groups(root: JSONObject, sound: Boolean, resource: (String) -> File?, recoverMissing: Boolean = false): List<LegacyGroup> {
        val array = root.optJSONArray("groups") ?: JSONArray()
        require(array.length() <= 128) { "分组过多" }
        var count = 0
        return (0 until array.length()).map { index ->
            val group = array.getJSONObject(index)
            val title = group.optString("title").trim().ifBlank { if (sound) "旧版音效" else "旧版快捷文本" }
            require(title.length <= 80) { "分组名称过长" }
            val gid = legacyId("${if (sound) "audio" else "text"}/${group.optString("id", "$index")}/$title")
            val items = group.optJSONArray("items") ?: JSONArray()
            count += items.length(); require(count <= 10000) { "快捷文本过多" }
            val colors = group.optJSONArray("itemColors")
            LegacyGroup(gid, title, group.optString("icon"), (0 until items.length()).mapNotNull { i ->
                if (!sound) {
                    val text = items.getString(i)
                    require(text.length <= 32768) { "快捷文本过长" }
                    if (text.isBlank()) null else LegacyPhrase(legacyId("$gid/$i/$text"), text, legacyColor(colors?.opt(i)))
                } else {
                    val item = items.getJSONObject(i)
                    val name = item.optString("title").ifBlank { "旧版音效" }
                    require(name.length <= 128) { "音效名称过长" }
                    val path = item.optString("audioFile").ifBlank { item.optString("audioPath") }
                    val audio = resource(path)
                    if (audio == null && recoverMissing) return@mapNotNull null
                    require(audio != null) { "音效「$name」缺少音频文件，请使用包含音频的数据备份或音效包" }
                    LegacyPhrase(legacyId("$gid/${item.optString("id", "$i")}/$name"), name, audio = audio,
                        metadata = listOf("durationMs", "trimStartMs", "trimEndMs", "wakeWord").associate { "legacy.$it" to item.optString(it) })
                }
            })
        }
    }

    private fun cards(root: JSONObject, resource: (String) -> File?, recoverMissing: Boolean = false): List<LegacyCard> {
        val array = root.optJSONArray("cards") ?: JSONArray()
        require(array.length() <= 256) { "名片过多，请分批导入" }
        return (0 until array.length()).flatMap { index ->
            val card = array.getJSONObject(index)
            val title = card.optString("title"); val note = card.optString("note")
            require(title.length <= 128 && note.length <= 8192) { "名片文字过长" }
            val portrait = card.optString("portraitImage").ifBlank { card.optString("portraitImagePath") }
            val landscape = card.optString("landscapeImage").ifBlank { card.optString("landscapeImagePath") }
            val orientations = buildList { if (portrait.isNotBlank()) add(false to portrait); if (landscape.isNotBlank()) add(true to landscape); if (isEmpty()) add(false to "") }
            orientations.map { (wide, path) ->
                val image = if (path.isBlank()) null else resource(path)
                require(path.isBlank() || recoverMissing || image != null) { "名片「$title」缺少图片，请使用包含图片的备份" }
                val qr = card.optString("link").takeIf { card.optString("type") == "qr" }.orEmpty()
                require(qr.toByteArray(Charsets.UTF_8).size <= 2048) { "二维码内容过长" }
                LegacyCard(legacyId("card/${card.optString("id", "$index")}/$title/$note/$wide"), title, note,
                    legacyColor(card.opt("themeColor")) ?: 0xff038387L, image, wide, qr)
            }
        }
    }
}
