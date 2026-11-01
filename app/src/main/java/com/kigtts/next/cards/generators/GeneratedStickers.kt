package com.kigtts.next.cards.generators

import android.graphics.Bitmap
import com.kigtts.next.cards.StickerRepository
import com.kigtts.next.resources.ResourceArchive
import com.kigtts.plugin.api.*
import java.io.File
import java.util.UUID
import org.json.JSONObject

internal data class GeneratedSticker(val spec: VectorStickerGenerator, val directory: File, val values: Map<String, String>,
    val photoFields: Map<String, PhotoStickerField> = emptyMap(), val templateEntry: Boolean = false)

/** Fonts, templates and per-instance values are owned independently of plugin installation. */
internal class GeneratedStickers(private val stickers: StickerRepository) {
    private val root = File(stickers.root, "generators").apply { mkdirs() }
    private val prefs = stickers.context.getSharedPreferences("generated-stickers", 0)
    fun contains(imageId: String?) = imageId != null && prefs.contains(imageId)
    @Synchronized fun get(imageId: String?): GeneratedSticker? = runCatching {
        val record = imageId?.let { prefs.getString(it, null) }?.let(::JSONObject) ?: return@runCatching null
        val id = record.getString("template"); require(id.matches(Regex("[a-f0-9-]{36}")))
        val directory = File(root, id); val spec = PluginJson.decodeFromString<VectorStickerGenerator>(File(directory,"spec.json").readText())
        val values = record.getJSONObject("values").let { json -> json.keys().asSequence().associateWith { json.getString(it) } }
        val defaults = File(directory, PhotoStickerDefaults.FILE).takeIf { it.isFile }
            ?.let { PhotoStickerDefaults.read(it, listOf(spec))[spec.id] }.orEmpty()
        GeneratedSticker(spec, directory, values, defaults, record.optBoolean("templateEntry", false))
    }.getOrNull()
    fun install(archive: ResourceArchive): List<Pair<String, String>> = archive.generators.items.map { spec ->
        val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val template = File(directory, "template.svg"); archive.file(spec.template).copyTo(template)
            val fonts = spec.fonts.mapValues { (_, path) ->
                val target = File(directory, UUID.randomUUID().toString() + "." + File(path).extension)
                archive.file(path).copyTo(target); target.name
            }
            val local = spec.copy(template = template.name, fonts = fonts)
            File(directory, "spec.json").writeText(PluginJson.encodeToString(local), Charsets.UTF_8)
            val fields = archive.photoStickerDefaults[spec.id].orEmpty()
            if (fields.isNotEmpty()) File(directory, PhotoStickerDefaults.FILE).writeText(JSONObject()
                .put("schemaVersion", 1).put("generators", JSONObject().put(spec.id, JSONObject(fields.mapValues { it.value.name }))).toString(), Charsets.UTF_8)
            local.name to generate(GeneratedSticker(local, directory, emptyMap(), fields), emptyMap(), templateEntry = true)
        } catch (failure: Exception) { directory.deleteRecursively(); throw failure }
    }
    @Synchronized fun generate(source: GeneratedSticker, values: Map<String, String>, templateEntry: Boolean = false): String {
        val normalized = VectorStickerRenderer.values(source.spec, values)
        val bitmap = VectorStickerRenderer.render(source.spec, File(source.directory, source.spec.template),
            source.spec.fonts.mapValues { File(source.directory, it.value) }, normalized)
        val temporary = File.createTempFile("generated-sticker", ".png", stickers.context.cacheDir)
        try {
            temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val image = stickers.importResource(temporary)
            val json = JSONObject().put("template", source.directory.name).put("values", JSONObject(normalized)).put("templateEntry", templateEntry)
            check(prefs.edit().putString(image.id, json.toString()).commit()) { "贴纸参数未能保存" }
            return image.id
        } finally { temporary.delete(); bitmap.recycle() }
    }
}
