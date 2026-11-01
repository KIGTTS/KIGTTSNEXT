package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.plugins.control.AppControlHost
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HostPluginCompatibilityTest {
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }

    @Test fun describeReportsHostLimitsWithoutChangingInstalledPluginDeclarations() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val legacy = object : KigPlugin {
            override val descriptor = PluginDescriptor("legacy.test", "Legacy", "test", apiVersion = 31)
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val model = AppViewModel(app, listOf(legacy))
        val broker = AppControlHost(model.pluginManager) { model }.forPlugin("host.compatibility.test", setOf("app-control"))
        val data = (broker.request(AppCommand.Describe) as AppReply.Data).value
        assertEquals(PLUGIN_API_VERSION, data.getValue("pluginApi").jsonPrimitive.int)
        assertEquals(MIN_PLUGIN_API_VERSION, data.getValue("minimumPluginApi").jsonPrimitive.int)
        assertTrue(data.getValue("plugins").jsonArray.any { it.jsonObject["id"]?.jsonPrimitive?.content == "legacy.test" })
    }
}
