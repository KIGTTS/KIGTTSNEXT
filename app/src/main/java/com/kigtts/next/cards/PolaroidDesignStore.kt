package com.kigtts.next.cards

import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class PhotoTextField(val label: String) { DATE("日期"), TIME("时间"), DATE_TIME("日期时间"), LOCATION("地点"), WEATHER("天气") }
data class FrozenPhotoFacts(val capturedAt: Long? = null, val zone: String = ZoneId.systemDefault().id,
    val latitude: Double? = null, val longitude: Double? = null, val location: String? = null, val weather: String? = null) {
    fun text(field: PhotoTextField): String = when (field) {
        PhotoTextField.LOCATION -> location ?: "拍摄地点未知"
        PhotoTextField.WEATHER -> weather ?: "拍摄天气未知"
        else -> capturedAt?.let { instant ->
            val pattern = when (field) { PhotoTextField.DATE -> "yyyy.MM.dd"; PhotoTextField.TIME -> "HH:mm"; else -> "yyyy.MM.dd HH:mm" }
            DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(instant).atZone(ZoneId.of(zone)))
        } ?: "拍摄时间未知"
    }
}
data class PhotoFieldBinding(val field: PhotoTextField, val text: String)
data class PolaroidDesignPreset(val id: String, val name: String, val design: PolaroidCard,
    val bindings: Map<String, PhotoFieldBinding> = emptyMap())
data class PolaroidPresetState(val presets: List<PolaroidDesignPreset> = emptyList(), val defaultId: String? = null)

/** Capture facts and preset bindings are local; SDK14 PHOTO_INFO layers carry portable caption text. */
class PolaroidDesignStore(private val root: File) {
    private val file = File(root, "design-presets.json")
    private var json = load()
    private val mutable = MutableStateFlow(readState())
    val state = mutable.asStateFlow()
    private fun load(): JSONObject = try {
        if (file.isFile) { require(file.length() <= 2 * 1024 * 1024); JSONObject(file.readText(Charsets.UTF_8)).also { readState(it) } } else JSONObject()
    } catch (_: Exception) {
        if (file.isFile) file.copyTo(File(root, "design-presets-recovery-${System.currentTimeMillis()}.json"))
        JSONObject()
    }
    private fun readState(value: JSONObject = json): PolaroidPresetState {
        val list = value.optJSONArray("presets") ?: JSONArray()
        return PolaroidPresetState((0 until list.length()).map { i ->
            val item = list.getJSONObject(i)
            PolaroidDesignPreset(item.getString("id"), item.getString("name"), PluginJson.decodeFromString(item.getString("design")), bindings(item.optJSONObject("bindings")))
        }, value.optString("default").takeIf(String::isNotBlank))
    }
    private fun bindings(value: JSONObject?): Map<String, PhotoFieldBinding> = value?.keys()?.asSequence()?.associateWith { id ->
        val binding = value.getJSONObject(id); PhotoFieldBinding(PhotoTextField.valueOf(binding.getString("field")), binding.getString("text"))
    }.orEmpty()
    private fun bindingJson(values: Map<String, PhotoFieldBinding>) = JSONObject().also { all -> values.forEach { (id, value) ->
        all.put(id, JSONObject().put("field", value.field.name).put("text", value.text))
    } }
    private fun commit(next: JSONObject) {
        val text = next.toString(); require(text.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024) { "预设及拍摄信息过多" }
        check(root.mkdirs() || root.isDirectory)
        val temp = File(root, ".design-presets.tmp")
        try { temp.writeText(text, Charsets.UTF_8); Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) } finally { temp.delete() }
        json = next; mutable.value = readState()
    }
    @Synchronized fun hasFacts(cardId: String): Boolean = json.optJSONObject("photos")?.has(cardId) == true
    @Synchronized fun referencedFrames(): Set<String> = state.value.presets.map { it.design.frameStyleId }.toSet()
    @Synchronized fun facts(cardId: String): FrozenPhotoFacts {
        val value = json.optJSONObject("photos")?.optJSONObject(cardId) ?: return FrozenPhotoFacts()
        return FrozenPhotoFacts(value.optLong("at").takeIf { it > 0 }, value.optString("zone", ZoneId.systemDefault().id),
            if (value.has("lat")) value.getDouble("lat") else null, if (value.has("lon")) value.getDouble("lon") else null,
            value.optString("location").takeIf(String::isNotBlank), value.optString("weather").takeIf(String::isNotBlank))
    }
    @Synchronized fun freeze(cardId: String, facts: FrozenPhotoFacts) {
        val next = JSONObject(json.toString()); val photos = next.optJSONObject("photos") ?: JSONObject().also { next.put("photos", it) }
        photos.put(cardId, JSONObject().put("at", facts.capturedAt).put("zone", facts.zone).put("lat", facts.latitude).put("lon", facts.longitude)
            .put("location", facts.location).put("weather", facts.weather))
        commit(next)
    }
    @Synchronized fun bind(cardId: String, layer: CardLayer, field: PhotoTextField) {
        val next = JSONObject(json.toString()); val all = next.optJSONObject("bindings") ?: JSONObject().also { next.put("bindings", it) }
        val card = all.optJSONObject(cardId) ?: JSONObject().also { all.put(cardId, it) }
        card.put(layer.id, JSONObject().put("field", field.name).put("text", layer.text)); commit(next)
    }
    @Synchronized fun fieldLayerId(cardId: String, field: PhotoTextField): String? =
        bindings(json.optJSONObject("bindings")?.optJSONObject(cardId)).entries.firstOrNull { it.value.field == field }?.key
    @Synchronized fun fieldLayers(card: PolaroidCard, field: PhotoTextField): List<CardLayer> {
        val bound = bindings(json.optJSONObject("bindings")?.optJSONObject(card.id))
        return card.layers.filter { it.photoField() == field || bound[it.id]?.let { binding ->
            binding.field == field && (it.kind == CardLayerKind.PHOTO_INFO || binding.text == it.text)
        } == true }
    }
    @Synchronized fun normalizeFields(card: PolaroidCard): PolaroidCard {
        val bound = bindings(json.optJSONObject("bindings")?.optJSONObject(card.id))
        val fields = linkedMapOf<PhotoTextField, CardLayer>()
        val retained = card.layers.filter { layer ->
            val field = layer.photoField() ?: bound[layer.id]?.takeIf {
                layer.kind == CardLayerKind.PHOTO_INFO || layer.kind == CardLayerKind.TEXT && it.text == layer.text
            }?.field
            if (field != null) fields[field] = layer.copy(id = field.layerId(), kind = CardLayerKind.PHOTO_INFO)
            field == null
        }
        return card.copy(layers = retained + PhotoTextField.entries.mapNotNull(fields::get))
    }
    @Synchronized fun savePreset(name: String, card: PolaroidCard, makeDefault: Boolean): PolaroidDesignPreset {
        require(name.trim().length in 1..64 && state.value.presets.size < 64) { "请输入预设名称，最多保存64个预设" }
        val design = normalizeFields(card)
        val bound = design.layers.mapNotNull { layer -> layer.photoField()?.let { layer.id to PhotoFieldBinding(it, layer.text) } }.toMap()
        val preset = PolaroidDesignPreset(UUID.randomUUID().toString(), name.trim(),
            design.copy(id = "preset", title = "", description = "", imageId = null, originalImageId = null, photoX = .5, photoY = .5, photoZoom = 1.0), bound)
        val next = JSONObject(json.toString()); val list = next.optJSONArray("presets") ?: JSONArray().also { next.put("presets", it) }
        list.put(JSONObject().put("id", preset.id).put("name", preset.name).put("design", PluginJson.encodeToString(preset.design)).put("bindings", bindingJson(bound)))
        if (makeDefault) next.put("default", preset.id)
        commit(next); return preset
    }
    @Synchronized fun defaultPreset(id: String?) {
        require(id == null || state.value.presets.any { it.id == id }); commit(JSONObject(json.toString()).put("default", id))
    }
    @Synchronized fun removePreset(id: String) {
        val next = JSONObject(json.toString()); val list = next.optJSONArray("presets") ?: JSONArray()
        next.put("presets", JSONArray().also { kept -> (0 until list.length()).map(list::getJSONObject).filter { it.getString("id") != id }.forEach(kept::put) })
        if (next.optString("default") == id) next.remove("default")
        commit(next)
    }
    @Synchronized fun referencedImages(): Set<String> = state.value.presets.flatMap { it.design.layers.mapNotNull(CardLayer::imageId) }.toSet()
    @Synchronized fun requiredFields(): Set<PhotoTextField> = state.value.presets.firstOrNull { it.id == state.value.defaultId }?.bindings?.values?.map { it.field }?.toSet().orEmpty()
    @Synchronized fun apply(card: PolaroidCard, presetId: String? = state.value.defaultId): PolaroidCard {
        val preset = state.value.presets.firstOrNull { it.id == presetId } ?: return card
        val facts = facts(card.id)
        val newBindings = linkedMapOf<String, PhotoFieldBinding>()
        val layers = preset.design.layers.map { layer ->
            val binding = preset.bindings[layer.id]
            val id = binding?.field?.layerId() ?: UUID.randomUUID().toString()
            val text = binding?.let { facts.text(it.field) } ?: layer.text
            if (binding != null) newBindings[id] = binding.copy(text = text)
            layer.copy(id = id, text = text, kind = if (binding != null) CardLayerKind.PHOTO_INFO else layer.kind)
        }
        val next = JSONObject(json.toString()); val all = next.optJSONObject("bindings") ?: JSONObject().also { next.put("bindings", it) }
        val previous = bindings(all.optJSONObject(card.id))
        all.put(card.id, bindingJson(previous + newBindings)); commit(next)
        return card.copy(frameStyleId = preset.design.frameStyleId, frameColor = preset.design.frameColor, layers = layers)
    }
}
