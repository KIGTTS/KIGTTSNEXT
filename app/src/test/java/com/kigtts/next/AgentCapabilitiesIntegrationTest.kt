@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.state.*
import com.kigtts.plugin.api.*
import com.kigtts.tools.agent.*
import com.kigtts.engines.lua.LuaRuntimePlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentCapabilitiesIntegrationTest {
    private lateinit var model: AppViewModel
    private lateinit var env: PluginEnvironment
    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        listOf("quick-phrases", "agent-access").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        val luaEnv = object : PluginEnvironment {
            override val storage = com.kigtts.plugin.runtime.PluginPaths(app).storage("runtime.lua")
            override val assets = storage.directory("assets")
            override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("unused")
        }
        val lua = LuaRuntimePlugin(); lua.attach(luaEnv)
        model = AppViewModel(app, listOf(lua))
        env = object : PluginEnvironment {
            override val storage = model.pluginManager.paths.storage("tools.cloud-agent")
            override val assets = storage.directory("assets")
            override val quickTextBroker = model.quickTextPlugins.forPlugin("tools.cloud-agent", setOf("quick-text-read", "quick-text-write"))
            override val appBroker = com.kigtts.next.plugins.control.AppControlHost(model.pluginManager) { model }.forPlugin("tools.cloud-agent", setOf("app-control"))
            override suspend fun download(url: String, relativePath: String, sha256: String?): File = error("unused")
        }
    }
    @After fun end() { Dispatchers.resetMain() }
    @Test fun agentCreatesARealRenderablePageAndRepeatedCallUsesTheReceipt() = runBlocking {
        val tools = AgentTools(env)
        val call = ToolCall("calculator", "create_page", """{"title":"计算器","text":"计算一下","groupId":"","html":"<!doctype html><meta charset='utf-8'><button onclick='this.textContent=2+2'>计算</button>"}""")
        val result = tools.execute(call)
        assertTrue(result.content, result.content.contains("applied"))
        val item = model.phrases.value.single { it.title == "计算器" }
        assertEquals("计算一下", item.text)
        val media = requireNotNull(model.mediaRepository.get(item.mediaId))
        assertEquals(com.kigtts.next.media.MediaKind.HTML, media.kind)
        assertTrue(requireNotNull(model.mediaRepository.file(media)).readText().contains("onclick"))
        assertTrue(tools.execute(call).content.contains("applied"))
        assertEquals(1, model.phrases.value.count { it.title == "计算器" })
    }
    @Test fun luaNeedsNativeApprovalThenInstallsEnablesAndReturnsItsActualTestResult() = runBlocking {
        val tools = AgentTools(env)
        tools.execute(ToolCall("draft", "draft_lua", """{"manifest":{"id":"tools.integration","name":"测试工具","version":"1.0","permissions":[]},"source":"return {test=function(ctx) return {answer=42} end,execute=function(a,c) return {type='message',text='done'} end}"}"""))
        val delivery = ToolCall("deliver", "deliver_lua", """{"path":"drafts/tools.integration"}""")
        val denied = tools.execute(delivery)
        assertTrue(denied.content, denied.content.contains("APPROVAL_REQUIRED"))
        model.agentAccess.userAction("tools.cloud-agent", mapOf("_agentApprove" to "true"))
        val installed = tools.execute(delivery)
        assertTrue(installed.content, installed.content.contains("installed"))
        assertTrue(model.registry.plugin("runtime.lua")!!.contributions.managedPackages.single { it.id == "tools.integration" }.enabled)
        val result = tools.execute(ToolCall("test", "lua", """{"operation":"test","project":"tools.integration","action":"","input":""}"""))
        assertTrue(result.content, result.content.contains("42"))
        val run = tools.execute(ToolCall("run", "lua", """{"operation":"run","project":"tools.integration","action":"hello","input":""}"""))
        assertTrue(run.content, run.content.contains("需要用户批准"))
        model.agentAccess.userAction("tools.cloud-agent", mapOf("_agentAccess" to "full"))
        assertTrue(tools.execute(ToolCall("run2", "lua", """{"operation":"run","project":"tools.integration","action":"hello","input":""}""")).content.contains("done"))
    }
    @Test fun forwardedInputsCannotMintFullAccessOrSingleUseApproval() {
        model.agentAccess.userAction("tools.cloud-agent", mapOf("_callerPlugin" to "another", "_agentAccess" to "full", "_agentApprove" to "true"))
        assertFalse(model.agentAccess.full("tools.cloud-agent")); assertFalse(model.agentAccess.consume("tools.cloud-agent"))
    }
}
