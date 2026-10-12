package com.kigtts.plugin.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.PluginJson
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PluginPackageTest {
    private lateinit var paths: PluginPaths
    private lateinit var store: PluginPackageStore
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    @Before fun setup() {
        paths = PluginPaths(ApplicationProvider.getApplicationContext<Context>())
        paths.packages.deleteRecursively(); paths.packages.mkdirs(); paths.data.deleteRecursively(); paths.data.mkdirs()
        store = PluginPackageStore(paths)
    }
    private fun pack(version: Int = 1, tamper: Boolean = false, badPath: Boolean = false,
        otherPublisher: Boolean = false, api: Int = com.kigtts.plugin.api.PLUGIN_API_VERSION, capabilities: List<String> = emptyList()): File {
        val files = linkedMapOf("code/classes.dex" to byteArrayOf(1, 2, 3), "assets/readme.txt" to "test".toByteArray())
        if (badPath) files["../outside"] = byteArrayOf(4)
        val manifest = PackageManifest("test.engine", "测试引擎", "0.$version", version, "test.Engine", apiVersion = api,
            files = files.mapValues { it.value.sha256() }, capabilities = capabilities)
        val raw = PluginJson.encodeToString(manifest).toByteArray()
        val signing = if (otherPublisher) KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair() else key
        val file = File(paths.packages, ".package-$version-${System.nanoTime()}")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, data: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            put(MANIFEST, raw); put(PUBLIC_KEY, signing.public.encoded)
            put(SIGNATURE, Signature.getInstance("SHA256withRSA").run { initSign(signing.private); update(raw); sign() })
            files.forEach { (path, bytes) -> put(path, if (tamper && path.endsWith("dex")) byteArrayOf(9, 9, 9) else bytes) }
        }
        return file
    }
    @Test fun validPackageUpdatesAtomicallyAndUninstallCleansItsDataOnly() {
        val first = pack()
        val publisher = store.inspect(first).publisherSha256
        store.install(first, publisher)
        paths.storage("test.engine").file("models/model.onnx").apply { parentFile!!.mkdirs(); writeText("model") }
        paths.storage("other.engine").file("keep").writeText("other")
        assertEquals(1, store.verifyInstalled("test.engine").versionCode)
        store.enabled("test.engine", false); assertFalse(store.installed().single().enabled)
        store.install(pack(2), publisher)
        assertEquals(2, store.verifyInstalled("test.engine").versionCode)
        assertFalse("Updating a disabled plugin must keep it disabled",store.installed().single().enabled)
        assertEquals("model", paths.storage("test.engine").file("models/model.onnx").readText())
        store.uninstall("test.engine")
        assertFalse(File(paths.data, "test.engine").exists()); assertTrue(store.installed().isEmpty())
        assertEquals("other", paths.storage("other.engine").file("keep").readText())
    }
    @Test fun tamperingTraversalWrongApiAndPublisherReplacementAreRejected() {
        for (file in listOf(pack(tamper = true), pack(badPath = true), pack(api = 1), pack(api = 2), pack(api = 3), pack(api = 4), pack(api = 5),pack(api=6))) {
            assertThrows(IllegalArgumentException::class.java) { store.inspect(file) }
        }
        val valid = pack(); val publisher = store.inspect(valid).publisherSha256; store.install(valid, publisher)
        val replacement = pack(2, otherPublisher = true)
        assertThrows(IllegalArgumentException::class.java) { store.install(replacement, store.inspect(replacement).publisherSha256) }
        assertThrows(IllegalArgumentException::class.java) { store.install(pack(), publisher) }
        assertEquals(1, store.verifyInstalled("test.engine").versionCode)
    }
    @Test fun installedPayloadTamperingIsRejectedBeforeLoading() {
        val valid = pack(); store.install(valid, store.inspect(valid).publisherSha256)
        val file = File(store.currentDirectory("test.engine"), "assets/readme.txt"); file.writeText("changed")
        assertThrows(IllegalArgumentException::class.java) { store.verifyInstalled("test.engine") }
    }
    @Test fun extraUnsignedDexIsRejectedBeforeLoading() {
        val valid = pack(); store.install(valid, store.inspect(valid).publisherSha256)
        File(store.currentDirectory("test.engine"), "code/classes2.dex").writeBytes(byteArrayOf(9))
        assertThrows(IllegalArgumentException::class.java) { store.verifyInstalled("test.engine") }
    }
    @Test fun runtimeArtCacheIsDiscardedWhileSignedDexRemainsVerified() {
        val valid = pack(); store.install(valid, store.inspect(valid).publisherSha256)
        val directory = store.currentDirectory("test.engine")
        File(directory, "code/oat/arm64/classes.odex").apply { parentFile!!.mkdirs(); writeText("ART cache") }
        File(directory, "code/oat/classes.dex.cur.prof").writeText("profile")
        assertEquals(1, store.verifyInstalled("test.engine").versionCode)
        assertFalse(File(directory, "code/oat").exists())
        assertTrue(File(directory, "code/classes.dex").exists())
    }
    @Test fun specifiedAppdataDirectoryIsUsed() {
        assertTrue(paths.storage("test.engine").root.path.replace('\\', '/').endsWith("appdata/Kigtts/plugin/plugindata/test.engine"))
    }
    @Test fun readingMissingThumbnailNeverRecreatesUninstalledData() {
        assertNull(paths.existingResource("test.gone", "models/avatar.png"))
        assertFalse(java.io.File(paths.data, "test.gone").exists())
        assertThrows(IllegalArgumentException::class.java) { paths.existingResource("test.gone", "../outside") }
    }
    @Test fun disabledProviderCatalogKeepsPacksVisibleUntilUninstall() {
        val packageFile = pack(); store.install(packageFile, store.inspect(packageFile).publisherSha256)
        val plugin = object : com.kigtts.plugin.api.KigPlugin {
            override val descriptor = com.kigtts.plugin.api.PluginDescriptor("test.engine", "Engine", "test")
            override val tts = object : com.kigtts.plugin.api.TtsEngine {
                override val info = com.kigtts.plugin.api.TtsInfo(packs = listOf(com.kigtts.plugin.api.VoicePackInfo("pack", com.kigtts.plugin.api.VoicePackMetadata("语音包"), listOf("voice"))))
                override fun synthesize(request: com.kigtts.plugin.api.TtsRequest) = kotlinx.coroutines.flow.emptyFlow<com.kigtts.plugin.api.SynthesisEvent>()
            }
            override suspend fun execute(actionId: String, context: com.kigtts.plugin.api.PluginContext) = com.kigtts.plugin.api.PluginOutcome.RefreshUi
        }
        val catalog = ProviderCatalog(paths); catalog.save(plugin); store.enabled("test.engine", false)
        val cached = catalog.disabled(emptySet()).single()
        assertFalse(cached.enabled); assertEquals("语音包", cached.info.packs.single().metadata.name)
        store.uninstall("test.engine"); assertTrue(catalog.disabled(emptySet()).isEmpty())
    }
    @Test fun independentlyBuiltEnginePackagesPassSignatureAndPayloadVerification() {
        val directory = File(requireNotNull(System.getProperty("kig.plugin.packages")))
        org.junit.Assume.assumeTrue("Run tools/Build-EnginePlugins.ps1 first", directory.exists())
        val system = store.inspect(File(directory, "engine.system-tts-0.6.0.kigplugin"))
        val asr = store.inspect(File(directory, "engine.sensevoice-0.6.0.kigplugin"))
        val streaming = store.inspect(File(directory, "engine.streaming-asr-0.6.0.kigplugin"))
        val recognition = store.inspect(File(directory, "service.speech-recognition-0.6.0.kigplugin"))
        assertTrue("asr-service" in recognition.manifest.capabilities)
        assertTrue(recognition.manifest.files.keys.none { it.startsWith("lib/") || it.endsWith(".onnx") })
        assertEquals(system.publisherSha256, recognition.publisherSha256)
        assertEquals("engine.system-tts", system.manifest.id)
        assertEquals("engine.sensevoice", asr.manifest.id)
        assertTrue(asr.manifest.files.keys.any { it.endsWith("libsherpa-onnx-jni.so") })
        assertTrue(asr.manifest.files.keys.any { it.endsWith("libonnxruntime.so") })
        assertTrue(asr.manifest.files.keys.none { it.endsWith(".onnx") })
        assertTrue("asr-incremental" in streaming.manifest.capabilities)
        assertFalse("asr-segmented" in streaming.manifest.capabilities)
        assertEquals("engine.streaming-asr", streaming.manifest.id)
        assertTrue(streaming.manifest.files.keys.any { it.endsWith("libsherpa-onnx-jni.so") })
        assertTrue(streaming.manifest.files.keys.none { it.endsWith(".onnx") })
        assertEquals(system.publisherSha256, streaming.publisherSha256)
        val originalAbis = android.os.Build.SUPPORTED_ABIS
        val piper = try {
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "SUPPORTED_ABIS", arrayOf("arm64-v8a"))
            store.inspect(File(directory, "engine.piper-0.6.0.kigplugin"))
        } finally { org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build::class.java, "SUPPORTED_ABIS", originalAbis) }
        val service = store.inspect(File(directory, "service.speech-synthesis-0.6.0.kigplugin"))
        assertEquals(listOf("arm64-v8a"), piper.manifest.abis)
        assertTrue(piper.manifest.files.keys.any { it.endsWith("libespeak_jni.so") })
        assertTrue(piper.manifest.files.keys.contains("assets/espeak-ng-data.zip"))
        assertTrue(service.manifest.capabilities.contains("speech-service"))
        assertTrue(service.manifest.files.keys.none { it.startsWith("lib/") || it.endsWith(".onnx") })
        assertEquals(system.publisherSha256, service.publisherSha256)
        val example=File(directory,"tools.quick-text-example-0.6.0.kigplugin")
        if(example.isFile) {val tools=store.inspect(example);assertEquals(system.publisherSha256,tools.publisherSha256)
            assertEquals(com.kigtts.plugin.api.PLUGIN_API_VERSION,tools.manifest.apiVersion)
            assertTrue("quick-text-write" in tools.manifest.capabilities);assertFalse("network" in tools.manifest.capabilities)
            assertTrue(tools.manifest.files.keys.contains("assets/calculator.html"))}
    }
    @Test fun disabledRecognitionProvidersRemainManageableBeforeFirstLoadAndAfterCaching() {
        val input = pack(capabilities = listOf("asr-batch", "asr-incremental")); store.install(input, store.inspect(input).publisherSha256)
        store.enabled("test.engine", false); val catalog = ProviderCatalog(paths)
        assertEquals("test.engine", catalog.disabledAsr(emptySet()).single().pluginId)
        val plugin = object : com.kigtts.plugin.api.KigPlugin {
            override val descriptor = com.kigtts.plugin.api.PluginDescriptor("test.engine", "Recognizer", "test")
            override val asr = object : com.kigtts.plugin.api.AsrEngine {
                override suspend fun recognize(audio: com.kigtts.plugin.api.PcmAudio, config: com.kigtts.plugin.api.RecognitionConfig) = com.kigtts.plugin.api.RecognitionResult("")
                override fun recognizeStream(audio: kotlinx.coroutines.flow.Flow<com.kigtts.plugin.api.PcmAudio>, config: com.kigtts.plugin.api.RecognitionConfig) = kotlinx.coroutines.flow.emptyFlow<com.kigtts.plugin.api.RecognitionEvent>()
            }
            override val contributions = com.kigtts.plugin.api.PluginContributions(drawerEntries = listOf(com.kigtts.plugin.api.DrawerEntry("packs", "识别包", "packs", com.kigtts.plugin.api.PluginIcon.MIC)))
            override suspend fun execute(actionId: String, context: com.kigtts.plugin.api.PluginContext) = com.kigtts.plugin.api.PluginOutcome.RefreshUi
        }
        catalog.save(plugin); val cached = catalog.disabledAsr(emptySet()).single()
        assertFalse(cached.enabled); assertEquals("packs", cached.settingsPages.single().id)
        assertTrue(paths.storage("test.engine").file("config/host/asr-catalog.json").isFile)
        store.uninstall("test.engine"); assertTrue(catalog.disabledAsr(emptySet()).isEmpty())
    }
}
