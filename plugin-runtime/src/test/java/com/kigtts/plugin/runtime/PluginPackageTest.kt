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
        otherPublisher: Boolean = false, api: Int = 2): File {
        val files = linkedMapOf("code/classes.dex" to byteArrayOf(1, 2, 3), "assets/readme.txt" to "test".toByteArray())
        if (badPath) files["../outside"] = byteArrayOf(4)
        val manifest = PackageManifest("test.engine", "测试引擎", "0.$version", version, "test.Engine", apiVersion = api,
            files = files.mapValues { it.value.sha256() })
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
        assertEquals("model", paths.storage("test.engine").file("models/model.onnx").readText())
        store.uninstall("test.engine")
        assertFalse(File(paths.data, "test.engine").exists()); assertTrue(store.installed().isEmpty())
        assertEquals("other", paths.storage("other.engine").file("keep").readText())
    }
    @Test fun tamperingTraversalWrongApiAndPublisherReplacementAreRejected() {
        for (file in listOf(pack(tamper = true), pack(badPath = true), pack(api = 1))) {
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
    @Test fun specifiedAppdataDirectoryIsUsed() {
        assertTrue(paths.storage("test.engine").root.path.replace('\\', '/').endsWith("appdata/Kigtts/plugin/plugindata/test.engine"))
    }
    @Test fun independentlyBuiltEnginePackagesPassSignatureAndPayloadVerification() {
        val directory = File(requireNotNull(System.getProperty("kig.plugin.packages")))
        org.junit.Assume.assumeTrue("Run tools/Build-EnginePlugins.ps1 first", directory.exists())
        val system = store.inspect(File(directory, "engine.system-tts-0.1.0.kigplugin"))
        val asr = store.inspect(File(directory, "engine.sensevoice-0.1.0.kigplugin"))
        assertEquals("engine.system-tts", system.manifest.id)
        assertEquals("engine.sensevoice", asr.manifest.id)
        assertTrue(asr.manifest.files.keys.any { it.endsWith("libsherpa-onnx-jni.so") })
        assertTrue(asr.manifest.files.keys.any { it.endsWith("libonnxruntime.so") })
        assertTrue(asr.manifest.files.keys.none { it.endsWith(".onnx") })
    }
}
