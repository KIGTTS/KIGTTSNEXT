package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.kigtts.next.media.UiIcons
import com.kigtts.next.theme.*
import com.kigtts.next.ui.navigation.PageScaffold
import com.kigtts.next.ui.plugins.PluginControlsDialog
import com.kigtts.next.ui.plugins.PluginPageScreen
import com.kigtts.plugin.android.AndroidPluginEnvironment
import com.kigtts.plugin.api.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.net.URLClassLoader

/** Export-only source set; never included in the application or ordinary test suite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
class PluginArtworkCapture {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val captureSession = System.nanoTime()
    private val context = PluginContext("你好，很高兴认识你。", "你好，很高兴认识你。", ttsPluginId = "service.speech-synthesis")

    @Test fun exportRealPluginPagesAndCovers() = runBlocking {
        val manifest = File(requireNotNull(System.getenv("KIGTTS_ARTWORK_MANIFEST")))
        com.kigtts.next.ui.components.MaterialSymbols.initialize(app)
        val entries = Json.parseToJsonElement(manifest.readText()).jsonArray
        var page by mutableStateOf<PluginPage?>(null)
        var controls by mutableStateOf<PluginOutcome.Controls?>(null)
        var theme by mutableStateOf(ThemeMode.LIGHT)
        compose.setContent { KigttsTheme(ThemeSettings(seed = 0xFF038387L, mode = theme)) {
            Surface(Modifier.fillMaxSize()) { PageScaffold(page?.title ?: "LED 灯珠字幕", {}) {
                if (page != null) PluginPageScreen(page, invoke = { _, _ -> })
                controls?.let { PluginControlsDialog(it, { _, _ -> }, {}) }
            } }
        } }
        val report = mutableListOf<JsonObject>()
        entries.forEach { value ->
            val entry = value.jsonObject
            val id = entry.getValue("id").jsonPrimitive.content
            val folder = File(entry.getValue("output").jsonPrimitive.content)
            val environment = previewEnvironment(id, File(entry.getValue("assets").jsonPrimitive.content))
            if (id == "tools.package-store") {
                val catalog = File(entry.getValue("catalog").jsonPrimitive.content)
                environment.storage.file("catalogs/kigtts-official.json").apply { parentFile!!.mkdirs(); writeBytes(catalog.readBytes()) }
            }
            if (id == "tools.cloud-agent") {
                // Illustrative conversation in temporary storage; no credentials or API request.
                environment.storage.directory("config")
                environment.storage.directory("conversations")
                environment.storage.file("config/agent.json").writeText("""{"selectedConversation":"preview-demo"}""")
                environment.storage.file("conversations/preview-demo.json").writeText("""{
                    "id":"preview-demo", "title":"见面时想说的话", "messages":[
                      {"id":"demo-user","role":"user","content":"帮我准备几句初次见面时用的快捷文本，语气自然一点。"},
                      {"id":"demo-answer","role":"assistant","content":"当然，可以从这几句开始：\n\n**打个招呼**\n你好，很高兴认识你！\n\n**聊聊兴趣**\n你最近在玩什么游戏？\n\n**暂时离开**\n我去拿点东西，等会儿回来。\n\n想要更活泼一点，还是更简短一点？"}
                    ]} """)
            }
            val urls = entry.getValue("jars").jsonArray.map { File(it.jsonPrimitive.content).toURI().toURL() }
            URLClassLoader(urls.toTypedArray(), javaClass.classLoader).use { loader ->
                val plugin = loader.loadClass(entry.getValue("entryClass").jsonPrimitive.content).getDeclaredConstructor().newInstance() as KigPlugin
                try {
                    plugin.attach(environment)
                    val design = entry.getValue("design").jsonObject
                    val outcome = design["setupAction"]?.jsonPrimitive?.content?.let { plugin.execute(it, context) }
                    val requested = when (outcome) {
                        is PluginOutcome.OpenPluginPage -> outcome.pageId
                        is PluginOutcome.OpenPage -> outcome.pageId
                        else -> design["page"]?.jsonPrimitive?.content
                    }
                    val actual = requested?.let { plugin.page(it, context) }
                        ?: (plugin.contributions.settingsEntries.map { it.pageId } + plugin.contributions.drawerEntries.map { it.pageId })
                            .firstNotNullOfOrNull { plugin.page(it, context) }
                    val dialog = design["action"]?.jsonPrimitive?.content?.let { plugin.execute(it, context) as PluginOutcome.Controls }
                    check(actual != null || dialog != null) { "No preview page for $id" }
                    compose.runOnIdle { page = actual; controls = dialog }
                    listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                        compose.runOnIdle { theme = mode }
                        compose.waitForIdle()
                        val target = if (dialog != null) compose.onNode(isDialog()) else compose.onRoot()
                        target.captureRoboImage(File(folder, "preview-${mode.name.lowercase()}.png").path)
                    }
                    val cover = UiIcons.renderPreviewSvg(File(folder, "cover.svg").readText(), 1600)
                    File(folder, "cover.png").outputStream().use { cover.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    cover.recycle()
                    report += buildJsonObject { put("id", id); put("page", actual?.id ?: "LED 样式"); put("sourceCommit", entry.getValue("sourceCommit")); put("packageSha256", entry.getValue("packageSha256")) }
                    println("Artwork captured: $id")
                } finally { plugin.close() }
            }
        }
        File(manifest.parentFile, "capture-report.json").writeText(Json.encodeToString(JsonArray.serializer(), JsonArray(report)))
        Assert.assertEquals(15, report.size)
    }

    private fun previewEnvironment(id: String, resources: File) = object : AndroidPluginEnvironment {
        override val context = app
        override val storage = PluginStorage(File(app.cacheDir, "artwork/$captureSession/$id"))
        override val assets = resources
        override val appBroker = object : AppBroker {
            override suspend fun request(command: AppCommand): AppReply = AppReply.Failure("PREVIEW_ONLY", "")
        }
        override val speechBroker = object : SpeechBroker {
            override fun providers() = emptyList<TtsProvider>()
            override suspend fun command(pluginId: String, command: VoiceCommand): PluginOutcome = PluginOutcome.NoAction
            override fun synthesize(pluginId: String, request: TtsRequest): Flow<SynthesisEvent> = emptyFlow()
        }
        override val recognitionBroker = object : RecognitionBroker {
            override fun providers() = emptyList<AsrProvider>()
            override suspend fun recognize(pluginId: String, audio: PcmAudio, config: RecognitionConfig): RecognitionResult = error("Capture never starts microphones")
            override fun recognizeStream(pluginId: String, audio: Flow<PcmAudio>, config: RecognitionConfig): Flow<RecognitionEvent> = emptyFlow()
        }
        override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("Capture never downloads resources")
    }
}
