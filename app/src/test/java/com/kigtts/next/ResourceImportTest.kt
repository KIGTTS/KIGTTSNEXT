@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.resources.*
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.*
import java.util.zip.*

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [35], application = Application::class)
class ResourceImportTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    private fun signed(payload: Map<String, ByteArray>, content: ResourcePackageContent, type: ResourceType,
        hashes: Map<String, String>? = null, api: Int = 29): Pair<File, String> {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val files = payload + ("content.json" to PluginJson.encodeToString(content).toByteArray())
        val raw = PluginJson.encodeToString(ResourcePackageManifest(id = "test.resources", name = "测试资源", version = "1.0.0",
            versionCode = 1, apiVersion = api, resourceType = type, files = hashes ?: files.mapValues { hash(it.value) })).toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(raw); sign() }
        val file = File.createTempFile("resource-", ".kigresource", app.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            (files + mapOf("resource.json" to raw, "signature/public.der" to key.public.encoded, "signature/manifest.sig" to signature)).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return file to hash(key.public.encoded)
    }
    @Test fun rejectsTraversalTamperedPayloadAndWrongPublisherAndCleansStage() {
        val content = ResourcePackageContent(frames = listOf(PolaroidFrameStyle("frame", "边框")))
        val (file, publisher) = signed(emptyMap(), content, ResourceType.FRAME)
        val destination = File(app.cacheDir, "resource-verify")
        assertEquals(ResourceType.FRAME, ResourceArchive.read(file, destination, publisher).manifest.resourceType)
        destination.deleteRecursively()
        assertTrue(runCatching { ResourceArchive.read(file, destination, "a".repeat(64)) }.isFailure)
        assertFalse(destination.exists())
        val (bad, hash) = signed(mapOf("../escape" to byteArrayOf(1)), content, ResourceType.FRAME)
        assertTrue(runCatching { ResourceArchive.read(bad, destination, hash) }.isFailure)
        val (tampered, hash2) = signed(emptyMap(), content, ResourceType.FRAME, mapOf("content.json" to "0".repeat(64)))
        assertTrue(runCatching { ResourceArchive.read(tampered, destination, hash2) }.isFailure)
        assertFalse(destination.exists())
    }
    @Test fun quickTextsCanBeAddedTwiceWithDistinctIdentities() = runBlocking {
        val model = AppViewModel(app, emptyList())
        model.quickTexts.add("资源包重复添加", model.quickTexts.categories.value.first())
        val originalCount = model.phrases.value.count { it.text == "资源包重复添加" }
        val exported = model.quickTextBackup.prepareExport()
        val (file, publisher) = signed(mapOf("data/cards.kigcards" to exported.readBytes()),
            ResourcePackageContent(payload = "data/cards.kigcards"), ResourceType.QUICK_TEXT)
        repeat(2) { index ->
            val archive = ResourceArchive.read(file, File(app.cacheDir, "repeat-$index"), publisher)
            importResource(model, archive)
        }
        val copies = model.phrases.value.filter { it.text == "资源包重复添加" }
        assertEquals(originalCount * 3, copies.size)
        assertEquals(copies.size, copies.map { it.id }.distinct().size)
        assertFalse(model.registry.plugins.any { it.descriptor.id == "test.resources" })
    }
    @Test fun dataFrameImportDoesNotCreatePluginAndEachImportGetsNewIdentity() = runBlocking {
        val model = AppViewModel(app, emptyList())
        val (file, publisher) = signed(emptyMap(), ResourcePackageContent(frames = listOf(PolaroidFrameStyle("frame", "资源相框"))), ResourceType.FRAME)
        repeat(2) { index -> importResource(model, ResourceArchive.read(file, File(app.cacheDir, "frame-$index"), publisher)) }
        val frames = model.stickers.frameStyles.value.filter { it.name == "资源相框" }
        assertTrue(frames.size >= 2)
        assertEquals(frames.size, frames.map { it.id }.distinct().size)
        assertFalse(model.registry.plugins.any { it.descriptor.id == "test.resources" })
    }
    @Test fun personalFramesImportIntoTheirOwnLibraryAndGeneratorInstancesKeepIndependentValues() = runBlocking {
        val model = AppViewModel(app, emptyList())
        val frameStyles = model.stickers.frameStyles.value
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 576"><rect x="10" y="10" width="1004" height="556" rx="30" fill="none" stroke="#00696E" stroke-width="4"/></svg>""".toByteArray()
        val descriptor = PersonalCardFramePackage(items = listOf(PersonalCardFrameResource("fine", "资源名片边框", "fine.svg")))
        val (frame, publisher) = signed(mapOf("fine.svg" to svg, "card-frames.json" to PluginJson.encodeToString(descriptor).toByteArray()), ResourcePackageContent(), ResourceType.CARD_FRAME, api = 30)
        importResource(model, ResourceArchive.read(frame, File(app.cacheDir, "personal-frame"), publisher))
        assertEquals(frameStyles, model.stickers.frameStyles.value)
        assertNotNull(model.stickers.identityFrames.items.value.firstOrNull { it.name == "资源名片边框" }?.imageId)
        assertTrue(model.cards.cards.value.isEmpty())
        val template = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 64"><rect width="128" height="64" fill="{{ink}}"/></svg>""".toByteArray()
        val generators = StickerGenerators(items = listOf(VectorStickerGenerator("label", "参数字贴", "label.svg", 128, 64,
            listOf(StickerParameter("ink", "颜色", StickerParameterKind.COLOR, "#00696E")))))
        val (sticker, author) = signed(mapOf("label.svg" to template, "generators.json" to PluginJson.encodeToString(generators).toByteArray()), ResourcePackageContent(), ResourceType.STICKERS, api = 30)
        importResource(model, ResourceArchive.read(sticker, File(app.cacheDir, "generated-sticker"), author))
        val store = com.kigtts.next.cards.generators.GeneratedStickers(model.stickers)
        val item = model.stickers.snapshot.value.items.first { it.name == "参数字贴" }
        val original = store.get(item.imageId)!!
        val changed = store.generate(original, mapOf("ink" to "#FF0000"))
        assertNotEquals(item.imageId, changed)
        assertEquals("#00696E", store.get(item.imageId)!!.values["ink"])
        assertEquals("#FF0000", store.get(changed)!!.values["ink"])
        assertTrue(model.cards.cards.value.isEmpty())
        assertFalse(model.registry.plugins.any { it.descriptor.id == "test.resources" })
    }

}
