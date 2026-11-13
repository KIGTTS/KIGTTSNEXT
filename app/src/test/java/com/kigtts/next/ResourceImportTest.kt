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
    @Test fun signedFontAddsToAppearanceWithoutChangingSelectionOrCreatingPlugin() = runBlocking {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/fixtures/appearance-font/fixture.ttf")).use { it.readBytes() }
        val model = AppViewModel(app, emptyList())
        val before = model.state.value.theme
        val (file, publisher) = signed(mapOf("font.ttf" to bytes), ResourcePackageContent(payload = "font.ttf"), ResourceType.FONT, api = 32)
        repeat(2) { index -> importResource(model, ResourceArchive.read(file, File(app.cacheDir, "font-$index"), publisher)) }
        val fonts = com.kigtts.next.theme.InstalledFonts(app).list(before)
        assertEquals(1, fonts.count { it.name == "测试资源" })
        assertEquals(before, model.state.value.theme)
        assertFalse(model.registry.plugins.any { it.descriptor.id == "test.resources" })
        val (bad, owner) = signed(mapOf("font.ttf" to "invalid font bytes".toByteArray()), ResourcePackageContent(payload = "font.ttf"), ResourceType.FONT, api = 32)
        val archive = ResourceArchive.read(bad, File(app.cacheDir, "bad-font"), owner)
        assertTrue(runCatching { importResource(model, archive) }.isFailure)
        val (old, oldOwner) = signed(mapOf("font.ttf" to bytes), ResourcePackageContent(payload = "font.ttf"), ResourceType.FONT, api = 29)
        assertTrue(runCatching { ResourceArchive.read(old, File(app.cacheDir, "old-font"), oldOwner) }.isFailure)
    }
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
    private fun signedWatermark(id: String, name: String) = signed(
        mapOf("watermark.xml" to """<watermark version="1" id="$id" name="$name" layout="CLASSIC"/>""".toByteArray()),
        ResourcePackageContent(payload = "watermark.xml"), ResourceType.FRAME)

    @Test fun cameraWatermarkImportReplacesItsAssetWithoutCreatingAPlugin() = runBlocking {
        val model = AppViewModel(app, emptyList())
        val legacy = model.stickers.frameStyles.value
        val (file, publisher) = signedWatermark("test-frame", "资源水印")
        repeat(2) { index -> importResource(model, ResourceArchive.read(file, File(app.cacheDir, "frame-$index"), publisher)) }
        val frames = com.kigtts.next.cards.CameraWatermarkLibrary(app).choices()
        assertEquals(listOf("standard", "check-in", "classic"), frames.take(3).map { it.id })
        assertEquals(1, frames.count { it.name == "资源水印" && it.id.startsWith("kpm-") })
        assertEquals(legacy, model.stickers.frameStyles.value)
        val (old, owner) = signed(emptyMap(), ResourcePackageContent(frames = listOf(PolaroidFrameStyle("frame", "旧版相框"))), ResourceType.FRAME)
        assertTrue(runCatching { importResource(model, ResourceArchive.read(old, File(app.cacheDir, "old-frame"), owner)) }.isFailure)
        assertFalse(model.registry.plugins.any { it.descriptor.id == "test.resources" })
    }
    @Test fun slowImportCanLeaveDialogAndStillReportsCompletionWithoutStartingAnotherImport() = runBlocking {
        val model = AppViewModel(app, emptyList())
        val dispatcher = StandardTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val installs = ResourceInstalls(model, scope)
        val (archive, publisher) = signedWatermark("background-frame", "后台水印")
        val owner = "test.resource-owner"
        val file = model.pluginManager.paths.storage(owner).file("resource.kigresource")
        archive.copyTo(file, overwrite = true)
        val command = AppCommand.ResourcePackage(source = PluginResource("resource.kigresource"), expectedPublisher = publisher)
        try {
            val reply = installs.request(owner, command) as AppReply.Data
            val token = reply.value["token"]!!.toString().trim('"')
            installs.confirm()
            assertTrue(installs.prompt.value!!.busy)
            installs.dismiss()
            assertNull(installs.prompt.value)
            assertEquals("BUSY", (installs.request(owner, command) as AppReply.Failure).code)
            val completed = withTimeout(10000) {
                while (true) {
                    dispatcher.scheduler.runCurrent()
                    val status = installs.request(owner, AppCommand.ResourcePackage("status", token = token)) as AppReply.Data
                    if (status.value["state"].toString() == "\"COMPLETED\"") break
                    delay(10)
                }
                true
            }
            assertTrue(completed)
            assertNull(installs.prompt.value)
            assertTrue(com.kigtts.next.cards.CameraWatermarkLibrary(app).choices().any { it.name == "后台水印" })
        } finally { scope.cancel() }
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
        val template = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 64"><rect width="128" height="64" fill="{{ink}}"/><text x="10" y="40" font-size="20">{{time}}</text></svg>""".toByteArray()
        val generators = StickerGenerators(items = listOf(VectorStickerGenerator("label", "参数字贴", "label.svg", 128, 64,
            listOf(StickerParameter("ink", "颜色", StickerParameterKind.COLOR, "#00696E"),
                StickerParameter("time", "时间", StickerParameterKind.TEXT, "未知")))))
        val payload = mapOf("label.svg" to template, "generators.json" to PluginJson.encodeToString(generators).toByteArray(),
            "photo-defaults.json" to """{"schemaVersion":1,"generators":{"label":{"time":"TIME"}}}""".toByteArray())
        val (sticker, author) = signed(payload, ResourcePackageContent(), ResourceType.STICKERS, api = 34)
        importResource(model, ResourceArchive.read(sticker, File(app.cacheDir, "generated-sticker"), author))
        val store = com.kigtts.next.cards.generators.GeneratedStickers(model.stickers)
        val item = model.stickers.snapshot.value.items.first { it.name == "参数字贴" }
        val original = store.get(item.imageId)!!
        assertTrue(original.templateEntry)
        assertEquals(com.kigtts.next.cards.generators.PhotoStickerField.TIME, original.photoFields["time"])
        val changed = store.generate(original, original.values + mapOf("ink" to "#FF0000", "time" to "12:34"))
        assertNotEquals(item.imageId, changed)
        assertEquals("#00696E", store.get(item.imageId)!!.values["ink"])
        assertEquals("未知", store.get(item.imageId)!!.values["time"])
        assertEquals("#FF0000", store.get(changed)!!.values["ink"])
        assertEquals("12:34", store.get(changed)!!.values["time"])
        assertFalse(store.get(changed)!!.templateEntry)
        val (old, oldAuthor) = signed(payload, ResourcePackageContent(), ResourceType.STICKERS, api = 33)
        assertTrue(runCatching { ResourceArchive.read(old, File(app.cacheDir, "old-photo-defaults"), oldAuthor) }.isFailure)
        assertTrue(model.cards.cards.value.isEmpty())
        assertFalse(model.registry.plugins.any { it.descriptor.id == "test.resources" })
    }

}
