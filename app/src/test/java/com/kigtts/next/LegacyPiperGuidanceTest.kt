package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class LegacyPiperGuidanceTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    @Before fun reset() { app.getSharedPreferences("legacy-piper-guidance", 0).edit().clear().commit() }
    @Test fun onlyTheCheckboxPersistsAndEachColdLaunchCanPromptAgain() = runBlocking {
        val missing = setOf(MissingPiperComponent.SERVICE, MissingPiperComponent.ENGINE)
        var query = ""
        fun guide(gate: AtomicBoolean = AtomicBoolean(false)) = LegacyPiperGuidance(app, gate, { true }, { missing }, { query = it })
        val gate = AtomicBoolean(false)
        val first = guide(gate); first.onStartup(); assertEquals(missing, first.prompt.value)
        first.dismiss(false); first.onStartup(); assertNull(first.prompt.value)
        val recreated = guide(gate); recreated.onStartup(); assertNull(recreated.prompt.value)
        val restart = guide(); restart.onStartup(); assertEquals(missing, restart.prompt.value)
        restart.proceed(true); assertEquals("Piper", query)
        val afterOptOut = guide(); afterOptOut.onStartup(); assertNull(afterOptOut.prompt.value)
    }
    @Test fun noPacksOrNoMissingComponentsNeverPromptAndServiceAloneHasItsOwnInstallQuery() = runBlocking {
        var query = ""
        val noPacks = LegacyPiperGuidance(app, AtomicBoolean(), { false }, { setOf(MissingPiperComponent.ENGINE) }, { error("unexpected") })
        noPacks.onStartup(); assertNull(noPacks.prompt.value)
        val complete = LegacyPiperGuidance(app, AtomicBoolean(), { error("should not scan") }, { emptySet() }, {})
        complete.onStartup(); assertNull(complete.prompt.value)
        val service = LegacyPiperGuidance(app, AtomicBoolean(), { true }, { setOf(MissingPiperComponent.SERVICE) }, { query = it })
        service.onStartup(); service.proceed(false); assertEquals("语音合成服务", query)
    }
    @Test fun detectionReadsOnlyWhitelistedPackMetadataAndNeverChangesOriginalFiles() {
        val root = File(app.cacheDir, "piper-detection-test").apply { mkdirs() }
        val files = File(root, "files"); val data = File(root, "plugins")
        val pack = File(files, "models/voice/old-voice").apply { mkdirs() }
        val manifest = File(pack, "manifest.json")
        val content = """{"files":{"model":"model.onnx","config":"config.json"}}"""
        manifest.writeText(content); File(pack, "model.onnx").writeBytes(byteArrayOf(1)); File(pack, "config.json").writeText("{}")
        assertTrue(LegacyPiperPacks.exists(files, data)); assertEquals(content, manifest.readText())
        manifest.writeText("""{"files":{"model":"../../outside.onnx","config":"config.json"}}""")
        assertFalse(LegacyPiperPacks.exists(files, data))
        manifest.writeText(" ".repeat(65537)); assertFalse(LegacyPiperPacks.exists(files, data))
        val current = File(data, "engine.piper/models/voices/current").apply { mkdirs() }
        File(current, "manifest.json").writeText(content); File(current, "model.onnx").writeBytes(byteArrayOf(2)); File(current, "config.json").writeText("{}")
        assertTrue(LegacyPiperPacks.exists(files, data))
    }
}
