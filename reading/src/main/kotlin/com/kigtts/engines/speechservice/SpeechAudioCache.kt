// SPDX-License-Identifier: GPL-3.0-only
package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.PcmAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

internal data class CacheVoice(val owner: String, val pack: String, val revision: String, val voice: String?)
internal data class AudioCacheKey(val voice: CacheVoice, val phraseId: String?, val text: String,
    val language: String, val rate: Float, val requestedVoice: String?, val profileVersion: String? = null) {
    val quick = phraseId?.let { cacheHash(it) }
    val textHash = cacheHash(text)
    val requestHash = if (profileVersion == null) cacheHash(language, rate, requestedVoice)
        else cacheHash(language, rate, requestedVoice, profileVersion)
}

internal fun cacheHash(vararg parts: Any?): String = MessageDigest.getInstance("SHA-256").run {
    parts.forEach {
        val data = it?.toString()?.encodeToByteArray()
        update((data?.size ?: -1).toString().encodeToByteArray())
        update(0.toByte())
        if (data != null) update(data)
    }
    val hex = "0123456789abcdef"
    buildString(64) { for (byte in digest()) { append(hex[(byte.toInt() and 255) ushr 4]); append(hex[byte.toInt() and 15]) } }
}

/** Long-lived private audio. One atomic file per request, bounded streaming buffers and no stored text. */
internal class SpeechAudioCache(private val root: File, private val size: MutableStateFlow<Long> = MutableStateFlow(0L)) {
    private val staging = File(root, ".pending")
    val bytes = size.asStateFlow()
    private var epoch = 0L
    private var phrases: Map<String, String>? = null
    private var lastUse = 0L
    private var quickRecords = mutableMapOf<Pair<File, String>, File>()
    private var recentRecords = mutableSetOf<File>()
    init { staging.deleteRecursively(); refreshSize() }

    private fun packDir(owner: String, pack: String) = File(File(root, cacheHash(owner)), cacheHash(pack))
    private fun revisionDir(voice: CacheVoice) = File(packDir(voice.owner, voice.pack), voice.revision)
    private fun destination(key: AudioCacheKey) = File(File(revisionDir(key.voice), cacheHash(key.voice.voice)),
        if (key.quick != null) "q_${key.quick}_${key.textHash}_${key.requestHash}.gz" else "r_${key.textHash}_${key.requestHash}.gz")
    private fun records() = root.walkTopDown().onEnter { it != staging }.filter { it.isFile && it.extension == "gz" }
    private fun refreshSize() {
        val found = records().toList()
        recentRecords = found.filterTo(mutableSetOf()) { it.name.startsWith("r_") }
        lastUse = maxOf(lastUse, recentRecords.maxOfOrNull { it.lastModified() } ?: 0L)
        quickRecords = found.filter { it.name.startsWith("q_") }.associateByTo(mutableMapOf()) {
            requireNotNull(it.parentFile) to it.name.substringAfter('_').substringBefore('_')
        }
        size.value = found.sumOf { it.length() }
    }
    private fun touch(file: File) {
        lastUse = maxOf(System.currentTimeMillis(), lastUse + 1)
        file.setLastModified(lastUse)
    }

    @Synchronized fun has(key: AudioCacheKey) = destination(key).isFile
    @Synchronized fun open(key: AudioCacheKey): Reader? {
        val file = destination(key)
        if (!file.isFile) return null
        return try { Reader(file).also { if (key.quick == null) touch(file) } }
        catch (_: IOException) { file.delete(); refreshSize(); null }
    }
    @Synchronized fun discard(key: AudioCacheKey) { if (destination(key).delete()) refreshSize() }
    @Synchronized fun writer(key: AudioCacheKey): Writer? {
        val file = try { staging.mkdirs(); File.createTempFile("pcm", ".tmp", staging) }
        catch (_: IOException) { return null }
        return try { Writer(key, epoch, file) } catch (_: IOException) { file.delete(); null }
    }

    @Synchronized fun updatePhrases(items: Map<String, String>) {
        val hashes = items.entries.associate { cacheHash(it.key) to cacheHash(it.value) }
        if (hashes == phrases) return
        phrases = hashes
        records().filter { it.name.startsWith("q_") }.forEach { file ->
            val parts = file.name.split('_', limit = 4)
            if (parts.size != 4 || hashes[parts[1]] != parts[2]) file.delete()
        }
        refreshSize()
    }
    @Synchronized fun forgetPacks(owner: String, packs: Set<String>) {
        if (packs.isEmpty()) return
        epoch++
        packs.forEach { packDir(owner, it).deleteRecursively() }
        refreshSize()
    }
    @Synchronized fun forgetShortcuts(voice: CacheVoice) {
        epoch++
        // Restrict manual rebuild to the selected voice; keep its recent text and other voices.
        val key = cacheHash(voice.voice)
        packDir(voice.owner, voice.pack).listFiles().orEmpty().filter { it.isDirectory }.forEach { revision ->
            File(revision, key).listFiles().orEmpty().filter { it.name.startsWith("q_") && it.extension == "gz" }
                .forEach { it.delete() }
        }
        refreshSize()
    }
    @Synchronized fun reconcile(knownOwners: Set<String>?, voices: List<CacheVoice>) {
        val owners = knownOwners?.mapTo(hashSetOf(), ::cacheHashOne)
        var changed = false
        if (owners != null) root.listFiles().orEmpty().filter { it.isDirectory && it != staging && it.name !in owners }
            .forEach { it.deleteRecursively(); changed = true }
        for (voice in voices.distinctBy { it.owner to it.pack }) {
            packDir(voice.owner, voice.pack).listFiles().orEmpty().filter { it.isDirectory && it.name != voice.revision }
                .forEach { it.deleteRecursively(); changed = true }
        }
        if (changed) { epoch++; refreshSize() }
    }
    @Synchronized fun clear() { epoch++; root.deleteRecursively(); refreshSize() }
    @Synchronized fun suspendWrites() { epoch++ }

    @Synchronized private fun commit(key: AudioCacheKey, token: Long, file: File): Boolean {
        if (token != epoch || (key.quick != null && phrases?.let { it[key.quick] != key.textHash } == true)) return false
        val destination = destination(key)
        destination.parentFile?.mkdirs()
        val formerBytes = destination.length()
        Files.move(file.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        touch(destination)
        var bytes = size.value - formerBytes + destination.length()
        fun delete(old: File) { val previousBytes = old.length(); if (old.delete()) bytes -= previousBytes }
        if (key.quick == null) {
            recentRecords += destination
            recentRecords.sortedByDescending { it.lastModified() }.drop(30).forEach { delete(it); if (!it.exists()) recentRecords.remove(it) }
        } else {
            quickRecords.put(requireNotNull(destination.parentFile) to key.quick, destination)
                ?.takeIf { it != destination }?.let(::delete)
        }
        size.value = bytes
        return true
    }

    inner class Writer(private val key: AudioCacheKey, private val token: Long, private val file: File) : Closeable {
        private val output = DataOutputStream(GZIPOutputStream(file.outputStream().buffered(), 16_384))
        private var length = 0L
        private var valid = true
        init { output.writeInt(MAGIC) }
        fun append(audio: PcmAudio) {
            if (!valid || audio.bytes.isEmpty()) return
            length += audio.bytes.size
            if (length > MAX_AUDIO_BYTES) { valid = false; return }
            try {
                var offset = 0
                while (offset < audio.bytes.size) {
                    val count = minOf(CHUNK, audio.bytes.size - offset)
                    output.writeInt(count); output.writeInt(audio.sampleRate); output.writeByte(audio.channels)
                    output.write(audio.bytes, offset, count)
                    offset += count
                }
            } catch (_: IOException) { valid = false }
        }
        fun finish(): Boolean = try {
            if (valid && length > 0) {
                output.writeInt(0); output.close()
                commit(key, token, file)
            } else false
        } catch (_: IOException) { false }
        override fun close() { runCatching { output.close() }; file.delete() }
    }

    class Reader(file: File) : Closeable {
        private val input = DataInputStream(GZIPInputStream(file.inputStream().buffered(), 16_384))
        init {
            try { if (input.readInt() != MAGIC) throw IOException("Unknown audio cache format") }
            catch (e: IOException) { input.close(); throw e }
        }
        fun next(): PcmAudio? {
            val count = input.readInt()
            if (count == 0) {
                if (input.read() != -1) throw IOException("Trailing audio data")
                return null
            }
            val rate = input.readInt(); val channels = input.readUnsignedByte()
            if (count !in 1..CHUNK || rate !in 8000..192000 || channels !in 1..2 || count % (2 * channels) != 0)
                throw IOException("Invalid cached audio")
            return PcmAudio(ByteArray(count).also(input::readFully), rate, channels)
        }
        override fun close() = input.close()
    }

    private companion object {
        const val MAGIC = 0x4B545301
        const val CHUNK = 64 * 1024
        const val MAX_AUDIO_BYTES = 64L * 1024 * 1024
        fun cacheHashOne(value: String) = cacheHash(value)
    }
}
