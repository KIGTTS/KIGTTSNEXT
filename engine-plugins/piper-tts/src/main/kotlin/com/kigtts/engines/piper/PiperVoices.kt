// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.piper

import com.kigtts.plugin.api.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

internal class PiperVoices(private val storage: PluginStorage) {
    private val root = storage.directory("models/voices")
    private val settings = storage.file("config/selection.json").also { storage.configuration }
    var selected: String? = runCatching { JSONObject(settings.readText()).optString("voice").takeIf { it.isNotBlank() } }.getOrNull()
        private set
    fun list(): List<VoiceInfo> = root.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("voice-") }.flatMap { directory ->
        runCatching {
            val pack = PiperPack.read(directory)
            val metadata = File(directory, "voice-meta.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
            val name = metadata?.optString("name").orEmpty().ifBlank { directory.name }
            pack.speakers.map { (speaker, id) -> VoiceInfo("${directory.name}:$id",
                if (pack.speakers.size == 1) name else "$name · $speaker", pack.language,
                metadata?.optString("remark").orEmpty(), deletable = true, packName = name) }
        }.getOrDefault(emptyList())
    }.sortedBy { it.name }
    fun choose(id: String) { require(list().any { it.id == id }) { "语音包或声音不存在" }; selected = id; save() }
    fun resolve(id: String?): Pair<PiperPack, Int> {
        val voice = id ?: selected ?: list().firstOrNull()?.id ?: throw EngineException(EngineError.NOT_CONFIGURED, "Piper 尚未导入语音包")
        require(list().any { it.id == voice }) { "语音包不存在" }
        val parts = voice.split(':'); require(parts.size == 2)
        return PiperPack.read(File(root, parts[0])) to parts[1].toInt()
    }
    fun import(archive: File): VoiceInfo {
        require(archive.isFile && archive.length() in 1..2L * 1024 * 1024 * 1024)
        val staging = File(storage.temporary, "voice-import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().toList()
                require(entries.size in 1..4096 && entries.map { it.name }.distinct().size == entries.size)
                require(entries.sumOf { it.size } in 1..2L * 1024 * 1024 * 1024)
                entries.forEach { entry ->
                    val path = entry.name.removeSuffix("/")
                    require(path.isNotBlank() && !path.startsWith('/') && !path.contains(':') && !path.contains('\\') && path.split('/').none { it == ".." || it == "." || it.isEmpty() })
                    require(entry.size in 0..2L * 1024 * 1024 * 1024)
                    val target = File(staging, path)
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile!!.mkdirs()
                        zip.getInputStream(entry).use { input -> target.outputStream().use { require(input.copyTo(it) == entry.size) } }
                    }
                }
            }
            val directory = staging.walkTopDown().filter { it.isDirectory }.firstOrNull {
                File(it, "manifest.json").isFile || it.listFiles().orEmpty().any { file -> file.extension == "onnx" }
            } ?: error("未找到 Piper 语音包")
            PiperPack.read(directory)
            val oldMeta = File(directory, "meta.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
            val manifest = File(directory, "manifest.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
            File(directory, "voice-meta.json").writeText(JSONObject().put("name", oldMeta?.optString("name")?.takeIf { it.isNotBlank() }
                ?: manifest?.optString("name")?.takeIf { it.isNotBlank() } ?: "Piper 语音 ${list().map { it.id.substringBefore(':') }.distinct().size + 1}")
                .put("remark", oldMeta?.optString("remark").orEmpty()).toString())
            val id = "voice-${UUID.randomUUID()}"
            val destination = File(root, id)
            require(directory.renameTo(destination)) { "语音包安装失败" }
            val result = list().first { it.id.startsWith("$id:") }
            if (selected == null) choose(result.id)
            return result
        } finally { staging.deleteRecursively() }
    }
    fun delete(id: String) {
        resolve(id)
        val packId = id.substringBefore(':')
        require(File(root, packId).deleteRecursively()) { "语音包清理失败" }
        if (selected?.startsWith("$packId:") == true) { selected = list().firstOrNull()?.id; save() }
    }
    fun rename(id: String, name: String) {
        val pack = resolve(id).first
        require(name.trim().length in 1..80)
        val file = File(pack.directory, "voice-meta.json")
        val meta = JSONObject(file.readText()).put("name", name.trim()); file.writeText(meta.toString())
    }
    private fun save() { settings.writeText(JSONObject().put("voice", selected.orEmpty()).toString()) }
}
