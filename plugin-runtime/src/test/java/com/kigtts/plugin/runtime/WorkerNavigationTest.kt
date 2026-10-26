package com.kigtts.plugin.runtime

import android.content.Intent
import com.kigtts.plugin.api.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class WorkerNavigationTest {
    @Test fun accountAndVoiceCommandsPublishDynamicSelfPagesWithoutCachingOtherPluginsPages() = runBlocking {
        val owner = "test.cloud-tts"
        val plugin = object : KigPlugin {
            override val descriptor = PluginDescriptor(owner, "Cloud", "")
            override val contributions = PluginContributions(settingsEntries = listOf(SettingsEntry("settings", "账户", "settings")))
            override fun page(pageId: String, context: PluginContext) = PluginPage(pageId, pageId, emptyList())
            override suspend fun execute(actionId: String, context: PluginContext) = when (actionId) {
                "old" -> PluginOutcome.OpenPage("legacy")
                "other" -> PluginOutcome.OpenPluginPage("test.other", "private")
                else -> PluginOutcome.OpenPluginPage(owner, "provider/new-account")
            }
            override val voiceManager = object : VoiceManager {
                override suspend fun command(command: VoiceCommand) = PluginOutcome.OpenPluginPage(owner, "voice/new-voice")
            }
        }
        val controller = Robolectric.buildService(PluginWorkerService::class.java).create()
        val service = controller.get()
        PluginWorkerService::class.java.getDeclaredField("plugin").apply { isAccessible = true }.set(service, plugin)
        val wire = IPluginWorker.Stub.asInterface(service.onBind(Intent()))
        val context = PluginJson.encodeToString(PluginContext("", ""))
        fun ids() = PluginJson.decodeFromString<PluginSnapshot>(wire.snapshot(context)).pages.map { it.id }
        try {
            assertEquals(listOf("settings"), ids())
            wire.execute("account", context)
            assertTrue("provider/new-account" in ids())
            wire.execute("other", context)
            assertFalse("private" in ids())
            wire.execute("old", context)
            assertTrue("legacy" in ids())
            wire.voiceCommand(PluginJson.encodeToString(VoiceCommand(VoiceOperation.REFRESH)))
            assertTrue("voice/new-voice" in ids())
        } finally { controller.destroy() }
    }
}
