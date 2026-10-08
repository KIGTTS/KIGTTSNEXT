package com.kigtts.next

import com.kigtts.next.state.chooseEngine
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Test

class EngineChoicesTest {
    private fun plugin(id: String, role: TtsRole, availability: EngineAvailability = EngineAvailability.READY) = object : KigPlugin {
        override val descriptor = PluginDescriptor(id, id, "test")
        override val tts = object : TtsEngine {
            override val info = TtsInfo(availability = availability, role = role)
            override fun synthesize(request: TtsRequest) = kotlinx.coroutines.flow.emptyFlow<SynthesisEvent>()
        }
        override val contributions = PluginContributions()
        override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
    }
    @Test fun enabledServiceTakesOverPreviouslySelectedProvider() {
        val provider = plugin("test.provider", TtsRole.PROVIDER)
        val service = plugin("test.service", TtsRole.SERVICE, EngineAvailability.NEEDS_RESOURCES)
        assertEquals("test.service", chooseEngine(listOf(provider, service), "test.provider", true))
    }
    @Test fun disablingServiceFallsBackToProviderOrEmptyCatalog() {
        val provider = plugin("test.provider", TtsRole.PROVIDER)
        assertEquals("test.provider", chooseEngine(listOf(provider), "test.service", true))
        assertNull(chooseEngine(emptyList(), "test.service", true))
    }
    @Test fun activeTtsOnlyPluginIsNotAnEligibleAsrSelection() {
        assertNull(chooseEngine(listOf(plugin("test.provider", TtsRole.PROVIDER)), "test.provider", false))
    }
}
