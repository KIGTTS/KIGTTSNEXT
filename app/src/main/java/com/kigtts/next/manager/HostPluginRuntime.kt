package com.kigtts.next.manager

import android.app.Application
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.editingCardId
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference

/** The UI and external manager must share exactly one worker owner in the main process. */
class HostPluginRuntime private constructor(private val application: Application) {
    internal val legacyPiperStartupChecked = java.util.concurrent.atomic.AtomicBoolean(false)
    val quickTexts=com.kigtts.next.state.QuickTextLibrary(com.kigtts.next.state.QuickPhraseStore(application))
    val reading = com.kigtts.engines.speechservice.SpeechServicePlugin(
        shortcutTexts = flow { quickTexts.phrases.collect { phrases ->
            emit(phrases.filter { it.cardType() == com.kigtts.next.state.QuickCardType.TEXT && it.text.isNotBlank() }
                .associate { it.id to it.text })
        } }, installedProviders = ::voicePluginRevisions, mayCacheProvider = ::canBakeVoice)
    private val recognition = com.kigtts.engines.recognitionservice.RecognitionServicePlugin(::legacyRecognitionEnabled)
    val manager = PluginManager(application, listOf(reading, recognition), com.kigtts.next.sharing.pausedSharingPlugins)
    private fun legacyRecognitionEnabled(): Boolean? = manager.packages.installed()
        .firstOrNull { it.manifest.id == recognition.descriptor.id }?.enabled
    private fun voicePluginRevisions(): Map<String, String>? = if (!manager.installedCatalogReady) null
        else manager.status.associate { status ->
            val pkg = status.installed
            pkg.manifest.id to "${pkg.publisherSha256}/${pkg.manifest.versionCode}/${pkg.manifest.version}/${pkg.manifest.files.hashCode()}"
        }
    private fun canBakeVoice(id: String): Boolean {
        val installed = manager.status.firstOrNull { it.installed.manifest.id == id }?.installed ?: return false
        val owner = "${id}:${installed.publisherSha256}"
        return installed.enabled && manager.permissions.access(owner, "tts") == com.kigtts.plugin.runtime.PermissionAccess.ALLOW &&
            ("network" !in installed.manifest.capabilities ||
                manager.permissions.access(owner, "network") == com.kigtts.plugin.runtime.PermissionAccess.ALLOW)
    }
    val recovery = com.kigtts.next.recovery.CrashRecovery(application)
    val mediaRepository=com.kigtts.next.media.MediaRepository(application,manager.paths)
    val stickers = com.kigtts.next.cards.StickerRepository(application, manager.paths, mediaRepository)
    val cards = com.kigtts.next.cards.CardRepository(application, stickers)
    val stickerPlugins = com.kigtts.next.plugins.stickers.StickerHost(stickers, manager.paths,
        { target, id -> ui()?.cardUi?.open(target, id) ?: false },
        { state()?.editingCardId() }, { owner -> ui()?.agentAccess?.consume(owner) == true })
    val quickTextPlugins=com.kigtts.next.plugins.quicktext.QuickTextHost(quickTexts,mediaRepository,manager.paths,::ui)
    internal val browserHost = com.kigtts.next.plugins.control.BrowserHost(application, manager, mediaRepository, { quickTexts.phrases.value }, ::context)
    val appPlugins = com.kigtts.next.plugins.control.AppControlHost(manager, browserHost, ::ui)
    init {manager.safeMode = recovery::safeMode; manager.quickTextBrokerFactory=quickTextPlugins::forPlugin; manager.appBrokerFactory = appPlugins::forPlugin
        manager.stickerBrokerFactory = stickerPlugins::forPlugin }
    private val changes = MutableStateFlow(0); val revision = changes.asStateFlow()
    private val operations = Mutex()
    @Volatile private var owner = WeakReference<AppViewModel>(null)
    fun ui()=owner.get()
    fun attach(model: AppViewModel) { owner = WeakReference(model) }
    fun detach(model: AppViewModel) { if (owner.get() === model) owner.clear() }
    fun context() = owner.get()?.context() ?: com.kigtts.plugin.api.PluginContext("", "")
    fun state() = owner.get()?.state?.value
    suspend fun <T> mutate(action: suspend () -> T): T = operations.withLock {
        owner.get()?.stopSpeech()
        try { action() } finally { changes.value++ }
    }
    companion object {
        @Volatile private var instance: HostPluginRuntime? = null
        fun get(application: Application) = instance?.takeIf { it.application === application } ?: synchronized(this) {
            instance?.takeIf { it.application === application } ?: HostPluginRuntime(application).also { instance = it }
        }
    }
}
