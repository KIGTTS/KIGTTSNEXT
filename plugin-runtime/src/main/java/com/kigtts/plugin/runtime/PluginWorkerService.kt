package com.kigtts.plugin.runtime

import android.app.Service
import android.content.Intent
import android.os.*
import com.kigtts.plugin.api.*
import dalvik.system.DexClassLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** One plugin per disposable process; this is crash isolation, NOT an isolated-UID sandbox. */
open class PluginWorkerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var plugin: KigPlugin? = null
    private var installedDescriptor: PluginDescriptor? = null
    private var quickText:IHostQuickTextBroker?=null
    private var app: IHostAppBroker? = null
    private var stickers: IHostStickerBroker? = null
    private var observingUi: Job? = null
    private val taskForeground by lazy { PluginTaskForeground(this) }
    private val pages = mutableSetOf<String>()
    private data class Session(val job: Job, val descriptors: List<ParcelFileDescriptor>)
    private val sessions = ConcurrentHashMap<String, Session>()
    private val binder = object : IPluginWorker.Stub() {
        override fun attachQuickTextBroker(broker:IHostQuickTextBroker?) {check(plugin==null);quickText=broker}
        override fun attachAppBroker(broker: IHostAppBroker?) { check(plugin == null); app = broker }
        override fun attachStickerBroker(broker: IHostStickerBroker?) { check(plugin == null); stickers = broker }
        override fun observeUi(observer: IPluginUiObserver?) {
            observingUi?.cancel()
            if (observer != null) observingUi = scope.launch {
                try { requireNotNull(plugin).uiChanges.collect { observer.changed(it) } }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* The UI observer may detach without ending the plugin. */ }
            }
        }
        override fun ttsControl(command: String) = runBlocking(Dispatchers.IO) {
            require(command.length <= 8192)
            val engine = requireNotNull(plugin?.tts)
            if (engine.info.supportsControl) engine.control(PluginJson.decodeFromString(command))
        }
        override fun initialize(pluginId: String, broker: IHostSpeechBroker?): String = runBlocking(Dispatchers.IO) {
            check(plugin == null) { "工作进程已分配" }
            val paths = PluginPaths(this@PluginWorkerService)
            val store = PluginPackageStore(paths)
            val manifest = store.verifyInstalled(pluginId)
            val directory = store.currentDirectory(pluginId)
            val dex = manifest.files.keys.filter { it.matches(Regex("code/classes([2-9]|[1-9][0-9]+)?\\.dex")) }
                .sorted().map { File(directory, it) }
            require(dex.isNotEmpty())
            val abi = Build.SUPPORTED_ABIS.firstOrNull { File(directory, "lib/$it").isDirectory }
            val libraries = abi?.let { File(directory, "lib/$it").absolutePath }
            val loader = DexClassLoader(dex.joinToString(File.pathSeparator) { it.absolutePath },
                File(paths.storage(pluginId).temporary, "dex").apply { mkdirs() }.absolutePath, libraries, classLoader)
            val instance = loader.loadClass(manifest.entryClass).getDeclaredConstructor().newInstance() as KigPlugin
            installedDescriptor = packageDescriptor(instance.descriptor, manifest)
            plugin = instance
            try {
                instance.attach(ScopedEnvironment(this@PluginWorkerService, paths.storage(pluginId), File(directory, "assets"), "network" in manifest.capabilities,
                    if ("speech-service" in manifest.capabilities && broker != null) WorkerSpeechBroker(broker) else null,
                    if ("asr-service" in manifest.capabilities && broker != null) WorkerRecognitionBroker(broker) else null,
                    if(manifest.capabilities.any {it in setOf("quick-text-read","quick-text-write")})quickText?.let(::WorkerQuickTextBroker) else null,
                    if ("app-control" in manifest.capabilities) app?.let(::WorkerAppBroker) else null,
                    if (manifest.capabilities.any { it in setOf("stickers-read", "stickers-write") }) stickers?.let(::WorkerStickerBroker) else null))
                require(instance.tts == null || "tts" in manifest.capabilities) { "插件未声明 TTS 能力" }
                require(instance.tts?.info?.role != TtsRole.SERVICE || "speech-service" in manifest.capabilities) { "插件未声明语音合成服务能力" }
                require(instance.voiceManager == null || "voice-packs" in manifest.capabilities) { "插件未声明语音包管理能力" }
                require(instance.tts?.info?.managesVoices != true || instance.voiceManager != null) { "缺少已声明的语音管理接口" }
                require(instance.asr == null || "asr-batch" in manifest.capabilities || "asr-segmented" in manifest.capabilities || "asr-incremental" in manifest.capabilities)
                require(instance.asr?.info?.role != AsrRole.SERVICE || "asr-service" in manifest.capabilities) { "插件未声明语音识别服务能力" }
                require("asr-service" !in manifest.capabilities || instance.asr?.info?.role == AsrRole.SERVICE) { "识别服务必须实现 SERVICE 角色" }
            }
            catch (error: Exception) { instance.close(); plugin = null; throw error }
            scope.launch(Dispatchers.Main) {
                instance.uiChanges.collect {
                    val running = synchronized(pages) { pages.toList() }.any { id ->
                        instance.page(id, PluginContext("", ""))?.blocks?.filterIsInstance<PageBlock.Chat>()?.any { it.running || it.backgroundRunning } == true
                    }
                    try { taskForeground.update(running, instance.descriptor.name) }
                    catch (failure: Exception) { android.util.Log.w("KIGTTS", "Unable to keep active plugin task in foreground", failure) }
                }
            }
            snapshot(PluginJson.encodeToString(PluginContext("", "")))
        }
        override fun snapshot(context: String): String {
            require(context.toByteArray().size <= 256 * 1024)
            val instance = requireNotNull(plugin)
            val decoded = PluginJson.decodeFromString<PluginContext>(context)
            val contributions = instance.contributions(decoded)
            synchronized(pages) { pages.addAll(contributions.drawerEntries.map { it.pageId }); pages.addAll(contributions.settingsEntries.map { it.pageId }) }
            val snapshot = PluginSnapshot(installedDescriptor ?: instance.descriptor, contributions,
                synchronized(pages) { pages.mapNotNull { instance.page(it, decoded) } }, instance.tts?.info, instance.asr?.info, instance.localization)
            PluginRegistry(listOf(instance))
            return PluginJson.encodeToString(snapshot).also { require(it.toByteArray().size <= 512 * 1024) }
        }
        override fun execute(actionId: String, context: String): String = runBlocking(Dispatchers.IO) {
            require(context.toByteArray().size <= 256 * 1024 && actionId.length <= 256)
            val result = requireNotNull(plugin).execute(actionId, PluginJson.decodeFromString(context))
            if (result is PluginOutcome.OpenPage) synchronized(pages) { pages.add(result.pageId) }
            PluginJson.encodeToString(result)
        }
        override fun voiceCommand(command: String): String = runBlocking(Dispatchers.IO) {
            require(command.length <= 8192)
            PluginJson.encodeToString(requireNotNull(plugin?.voiceManager) { "此引擎不支持语音包管理" }
                .command(PluginJson.decodeFromString(command)))
        }
        override fun synthesize(request: String, token: String): ParcelFileDescriptor = launchSession(token) { output ->
            val engine = requireNotNull(plugin?.tts) { "插件没有 TTS 能力" }
            engine.synthesize(PluginJson.decodeFromString(request)).collect { event ->
                when (event) {
                    SynthesisEvent.Started -> { output.writeInt(AudioWire.START); output.flush() }
                    is SynthesisEvent.Audio -> AudioWire.pcm(output, event.audio)
                    is SynthesisEvent.Finished -> AudioWire.text(output, AudioWire.FINISH, event.notice.orEmpty())
                }
            }
        }
        override fun recognize(input: ParcelFileDescriptor, streaming: Boolean, config: String, token: String): ParcelFileDescriptor =
            launchSession(token, listOf(input)) { output ->
                val engine = requireNotNull(plugin?.asr) { "插件没有 ASR 能力" }
                val settings = PluginJson.decodeFromString<RecognitionConfig>(config)
                transferRecognition(input, output, engine, streaming, settings)
            }
        override fun cancel(token: String) { sessions.remove(token)?.let { session -> session.descriptors.forEach { runCatching { it.close() } }; session.job.cancel() } }
        override fun shutdown() {
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 4_000)
            scope.launch {
                val active = sessions.keys.toList()
                val jobs = sessions.values.map { it.job }
                active.forEach(::cancel)
                if (withTimeoutOrNull(3_000) { jobs.joinAll(); true } == true) runCatching { plugin?.close() }
                plugin = null
                // Exit guarantees native libraries and model mappings are no longer resident.
                Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 100)
            }
        }
    }
    private fun launchSession(token: String, inputs: List<ParcelFileDescriptor> = emptyList(),
        block: suspend (DataOutputStream) -> Unit): ParcelFileDescriptor {
        require(token.length in 1..80 && !sessions.containsKey(token))
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            DataOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])).use { output ->
                try { block(output) }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { if (isActive) runCatching { AudioWire.error(output, error) } }
                finally { inputs.forEach { runCatching { it.close() } }; sessions.remove(token) }
            }
        }
        sessions[token] = Session(job, inputs + pipe[1]); job.start()
        return pipe[0]
    }
    override fun onBind(intent: Intent) = binder
    override fun onTimeout(startId: Int, fgsType: Int) {
        taskForeground.close(); scope.launch { plugin?.close() }; stopSelf()
    }
    override fun onDestroy() { taskForeground.close(); scope.cancel(); super.onDestroy() }
}
class PluginWorker0 : PluginWorkerService()
class PluginWorker1 : PluginWorkerService()
class PluginWorker2 : PluginWorkerService()
class PluginWorker3 : PluginWorkerService()
class PluginWorker4 : PluginWorkerService()
class PluginWorker5 : PluginWorkerService()
class PluginWorker6 : PluginWorkerService()
class PluginWorker7 : PluginWorkerService()
class PluginWorker8 : PluginWorkerService()
class PluginWorker9 : PluginWorkerService()
class PluginWorker10 : PluginWorkerService()
class PluginWorker11 : PluginWorkerService()
