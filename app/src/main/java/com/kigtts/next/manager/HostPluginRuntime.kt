package com.kigtts.next.manager

import android.app.Application
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.builtinPlugins
import com.kigtts.next.state.editingCardId
import com.kigtts.plugin.runtime.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference

/** The UI and external manager must share exactly one worker owner in the main process. */
class HostPluginRuntime private constructor(private val application: Application) {
    val manager = PluginManager(application, builtinPlugins(application))
    val recovery = com.kigtts.next.recovery.CrashRecovery(application)
    val quickTexts=com.kigtts.next.state.QuickTextLibrary(com.kigtts.next.state.QuickPhraseStore(application))
    val mediaRepository=com.kigtts.next.media.MediaRepository(application,manager.paths)
    val stickers = com.kigtts.next.cards.StickerRepository(application, manager.paths, mediaRepository)
    val cards = com.kigtts.next.cards.CardRepository(application, stickers)
    val stickerPlugins = com.kigtts.next.plugins.stickers.StickerHost(stickers, manager.paths,
        { target, id -> ui()?.cardUi?.open(target, id) ?: false },
        { state()?.editingCardId() }, { owner -> ui()?.agentAccess?.consume(owner) == true })
    val quickTextPlugins=com.kigtts.next.plugins.quicktext.QuickTextHost(quickTexts,mediaRepository,manager.paths,::ui)
    val appPlugins = com.kigtts.next.plugins.control.AppControlHost(manager, ::ui)
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
