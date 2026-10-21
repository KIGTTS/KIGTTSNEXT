@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.plugins.control.AppControlHost
import com.kigtts.next.recovery.CrashRecovery
import com.kigtts.next.state.AppViewModel
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class PluginPermissionRecoveryTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun before() { app.getSharedPreferences("plugin-permissions", 0).edit().clear().commit(); File(app.filesDir, "recovery").deleteRecursively(); Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { Dispatchers.resetMain() }
    @Test fun onceResumesOnlyThatCallAndPersistentGrantsStayOwnerBound() = runTest {
        val ledger = PluginPermissions(app).apply { foreground = true }
        val first = async { ledger.request("plugin-a", "network", "A", "下载图片") }; runCurrent()
        assertFalse(first.isCompleted); assertEquals("下载图片", ledger.pending.value.single().purpose)
        ledger.decide(ledger.pending.value.single().token, PermissionAccess.ASK); assertTrue(first.await())
        assertEquals(PermissionAccess.ASK, ledger.access("plugin-a", "network"))
        val second = async { ledger.request("plugin-a", "network", "A", "下载图片") }; runCurrent()
        ledger.decide(ledger.pending.value.single().token, PermissionAccess.ALLOW); assertTrue(second.await())
        assertTrue(PluginPermissions(app).request("plugin-a", "network", "A", "下载图片"))
        assertEquals(PermissionAccess.ASK, ledger.access("plugin-b", "network"))
        ledger.set("plugin-a", "network", PermissionAccess.DENY); assertFalse(ledger.request("plugin-a", "network", "A", "下载图片"))
        ledger.set("plugin-a", "network", PermissionAccess.ASK)
        val background = async { ledger.request("plugin-a", "network", "A", "用途") }; runCurrent(); ledger.background()
        assertFalse(background.await()); assertTrue(ledger.pending.value.isEmpty())
    }
    @Test fun managedProjectDoesNotChangeAppBeforeGrantOrBorrowAnotherProjectsPermission() = runTest {
        val runtime = object : KigPlugin {
            override val descriptor = PluginDescriptor("runtime.test", "运行时", "测试")
            override val contributions = PluginContributions(managedPackages = listOf("first", "second").map {
                ManagedPackage(it, "1", "lua", true, it, setOf("app-control"), mapOf("app-control" to "按按钮提交字幕"), "project/$it", "enable/$it", "remove/$it")
            })
            override suspend fun execute(actionId: String, context: PluginContext) = PluginOutcome.RefreshUi
        }
        val model = AppViewModel(app, listOf(runtime)); val ledger = model.pluginManager.permissions.apply { foreground = true }
        val broker = AppControlHost(model.pluginManager) { model }.forPlugin("runtime.test", setOf("app-control"))
        val command = PluginJson.encodeToJsonElement<AppCommand>(AppCommand.SetText("授权之后")).jsonObject
        val task = async { broker.request(AppCommand.ManagedCall("first", "app", command)) }; runCurrent()
        assertEquals("", model.state.value.editorText); assertFalse(task.isCompleted)
        assertEquals("按按钮提交字幕", ledger.pending.value.single().purpose)
        ledger.decide(ledger.pending.value.single().token, PermissionAccess.ALLOW); task.await()
        assertEquals("授权之后", model.state.value.editorText)
        val other = async { broker.request(AppCommand.ManagedCall("second", "app", PluginJson.encodeToJsonElement<AppCommand>(AppCommand.SetText("拒绝后不应写入")).jsonObject)) }; runCurrent()
        ledger.decide(ledger.pending.value.single().token, PermissionAccess.DENY)
        assertTrue(other.await() is AppReply.Failure); assertEquals("授权之后", model.state.value.editorText)
        assertTrue(broker.request(AppCommand.ManagedCall("missing", "app", command)) is AppReply.Failure)
        val rows = com.kigtts.next.ui.plugins.pluginManagementItems(model)
        assertTrue(rows.any { it.id == "runtime.test::first" && it.toggle != null && it.actions.any { action -> action.id == "permissions/runtime.test/first" } })
    }
    @Test fun threeRecentStartupCrashesEnableSafeModeWithoutCountingNormalLongRunningExit() {
        var clock = 1_000_000L
        repeat(2) { CrashRecovery(app) { clock }.record(IllegalStateException("secret must not be stored"), true); clock += 1000 }
        assertFalse(CrashRecovery(app) { clock }.safeMode())
        CrashRecovery(app) { clock }.record(IllegalArgumentException("private"), true)
        val recovered = CrashRecovery(app) { clock }; assertTrue(recovered.report(), recovered.safeMode()); assertFalse(recovered.report().contains("secret must not be stored"))
        recovered.restore(); assertFalse(recovered.safeMode())
        val running = CrashRecovery(app) { clock }; clock += 60_000; repeat(5) { running.record(IllegalStateException(), true) }
        assertFalse(recovered.safeMode())
        repeat(3) { recovered.record(IllegalStateException(), false) }; assertTrue(recovered.safeMode())
    }
}
