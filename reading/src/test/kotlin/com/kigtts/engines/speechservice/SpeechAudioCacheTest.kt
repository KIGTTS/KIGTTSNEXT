package com.kigtts.engines.speechservice

import com.kigtts.plugin.api.PcmAudio
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechAudioCacheTest {
    @get:Rule val files = TemporaryFolder()
    private val a = CacheVoice("one", "pack-a", "old", "v")
    private val b = CacheVoice("two", "pack-b", "old", "v")
    private fun key(text: String, voice: CacheVoice = a, id: String? = null) =
        AudioCacheKey(voice, id, text, "zh-CN", 1f, null)
    private fun put(store: SpeechAudioCache, key: AudioCacheKey) = store.writer(key)!!.use {
        it.append(PcmAudio(byteArrayOf(1, 0)))
        assertTrue(it.finish())
    }

    @Test fun shortcutsHaveIndependentIdentityAndEditsDoNotEvictOtherEntriesOrPacks() {
        val directory = files.newFolder()
        val store = SpeechAudioCache(directory)
        store.updatePhrases(mapOf("x" to "相同", "y" to "相同"))
        for (voice in listOf(a, b)) for (id in listOf("x", "y")) put(store, key("相同", voice, id))
        assertTrue(store.bytes.value > 0)
        val reopened = SpeechAudioCache(directory)
        val stereo = ByteArray(80_000) { it.toByte() }
        reopened.writer(key("stereo"))!!.use { writer ->
            writer.append(PcmAudio(stereo, 48_000, 2)); assertTrue(writer.finish())
        }
        reopened.open(key("stereo"))!!.use { reader ->
            val chunks = generateSequence(reader::next).toList()
            assertTrue(chunks.all { it.sampleRate == 48_000 && it.channels == 2 })
            assertArrayEquals(stereo, chunks.flatMap { it.bytes.asList() }.toByteArray())
        }
        reopened.updatePhrases(mapOf("x" to "改过", "y" to "相同"))
        for (voice in listOf(a, b)) {
            assertFalse(reopened.has(key("相同", voice, "x")))
            assertTrue(reopened.has(key("相同", voice, "y")))
        }
        reopened.reconcile(null, emptyList())
        assertTrue(reopened.has(key("相同", b, "y")))
        reopened.reconcile(setOf("one", "two"), listOf(a.copy(revision = "new")))
        assertFalse(reopened.has(key("相同", a, "y")))
        assertTrue(reopened.has(key("相同", b, "y")))
        reopened.forgetPacks("two", setOf("pack-b"))
        assertEquals(0L, reopened.bytes.value)
    }

    @Test fun retainsThirtyRecentRequestsAcrossPacksAndClearRejectsPendingWrites() {
        val store = SpeechAudioCache(files.newFolder())
        put(store, key("固定短语", id = "p"))
        val recent = (1..30).map { key("文本$it", if (it % 2 == 0) a else b) }
        recent.forEach { put(store, it) }
        store.open(recent[0])!!.close()
        put(store, key("最新"))
        assertTrue(store.has(recent[0]))
        assertFalse(store.has(recent[1]))
        assertTrue(store.has(key("固定短语", id = "p")))
        val pending = store.writer(key("正在生成"))!!
        pending.use { it.append(PcmAudio(byteArrayOf(1, 0))); store.clear(); assertFalse(it.finish()) }
        assertEquals(0L, store.bytes.value)
        assertFalse(store.has(key("正在生成")))
    }
}
