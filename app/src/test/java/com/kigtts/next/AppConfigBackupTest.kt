@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kigtts.next

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.backup.CONFIG_BACKUP_TYPE
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.theme.AppearanceAssets
import com.kigtts.next.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppConfigBackupTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun configurationAndEmbeddedBackgroundRoundTripWithoutRestoringCredentials() = runBlocking {
        val model = AppViewModel(app)
        val bitmap = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888)
        val key = try { AppearanceAssets(app).saveBackground(bitmap) } finally { bitmap.recycle() }
        val original = model.state.value.theme.copy(mode = ThemeMode.DARK, background = key, seed = 0xff123456L)
        model.theme(original)
        model.quickSort.choose(true)
        val readingSettings = model.pluginManager.paths.storage("service.speech-synthesis").file("config/service.json")
        readingSettings.parentFile!!.mkdirs()
        readingSettings.writeText("""{"rate":1.25,"cacheEnabled":false,"resident":true,"idleMinutes":4}""")
        val recognitionSettings = model.pluginManager.paths.storage("service.speech-recognition").file("config/recognition-service.json")
        recognitionSettings.parentFile!!.mkdirs()
        recognitionSettings.writeText("""{"routingDisabled":true,"voiceInput":false,"order":["engine.system-asr"]}""")
        app.getSharedPreferences("agent-access", 0).edit().putString("secret", "private-marker").commit()
        val archive = model.backupRestore.prepareConfigExport()
        java.util.zip.ZipFile(archive).use { zip ->
            val settings = zip.getInputStream(zip.getEntry("settings.json")).bufferedReader().use { it.readText() }
            assertFalse(settings.contains("private-marker"))
            assertEquals(1.25, JSONObject(settings).getJSONObject("reading-service").getDouble("rate"), 0.0)
        }
        model.theme(original.copy(mode = ThemeMode.LIGHT, background = null))
        model.quickSort.choose(false)
        readingSettings.writeText("""{"rate":0.75,"cacheEnabled":true}""")
        recognitionSettings.writeText("""{"routingDisabled":false}""")
        check(requireNotNull(AppearanceAssets(app).file(key)).delete())
        model.backupRestore.offer(Uri.fromFile(archive))
        assertEquals(ThemeMode.LIGHT, model.state.value.theme.mode)
        assertNull(AppearanceAssets(app).file(key))
        model.backupRestore.confirmConfig()
        assertEquals(original, model.state.value.theme)
        assertTrue(model.quickSort.enabled.value == true)
        assertNotNull(AppearanceAssets(app).file(key))
        assertEquals(1.25, JSONObject(readingSettings.readText()).getDouble("rate"), 0.0)
        assertTrue(JSONObject(recognitionSettings.readText()).getBoolean("routingDisabled"))
    }

    @Test fun sharedRestorePickerReadsTypedOriginalConfigAndRecoversWithoutOmittedAttachments() = runBlocking {
        val model = AppViewModel(app)
        val manifest = JSONObject().put("type", "kigtts_config_backup").put("version", 2)
        val entries = JSONArray().put(preference("theme_mode", "int", 2)).put(preference("haptic_feedback_enabled", "boolean", false))
            .put(preference("original_only", "string_set", JSONArray(listOf("one", "two"))))
            .put(preference("quick_subtitle_config", "string", """{"groups":[{"title":"旧版分组","items":["你好"]}]}"""))
            .put(preference("quick_card_config", "string", """{"cards":[{"title":"没附图","portraitImagePath":"/data/user/0/example/files/quick_cards/absent.png"}]}"""))
        val old = zip(manifest, "preferences.json", JSONObject().put("version", 1).put("entries", entries))
        model.backupRestore.offer(Uri.fromFile(old))
        val preview = requireNotNull(model.legacyImports.preview.value)
        assertEquals(setOf("one", "two"), preview.preferences["original_only"])
        assertEquals("你好", preview.groups.single().items.single().text)
        assertNull(preview.cards.single().image)
        assertEquals(listOf("backup.legacy_missing_media"), preview.warnings)
        assertNull(model.backupRestore.pendingConfig.value)
        assertNull(model.quickTextBackup.pendingImport.value)
    }

    @Test fun malformedTypedSettingsAreRejectedBeforeAnyChange() = runBlocking {
        val model = AppViewModel(app)
        val before = model.state.value.theme
        val bad = zip(JSONObject().put("type", CONFIG_BACKUP_TYPE).put("version", 1), "settings.json",
            JSONObject().put("appearance", JSONObject().put("mode", "DARK").put("seed", JSONObject())))
        assertTrue(runCatching { model.backupRestore.offer(Uri.fromFile(bad)) }.isFailure)
        assertNull(model.backupRestore.pendingConfig.value)
        assertEquals(before, model.state.value.theme)
        assertFalse(model.backupRestore.busy.value)
    }

    private fun preference(name: String, type: String, value: Any) =
        JSONObject().put("name", name).put("type", type).put("value", value)

    private fun zip(manifest: JSONObject, filename: String, payload: JSONObject): File =
        File.createTempFile("config-backup-test", ".zip", app.cacheDir).also { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                mapOf("manifest.json" to manifest, filename to payload).forEach { (name, json) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(json.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
}
