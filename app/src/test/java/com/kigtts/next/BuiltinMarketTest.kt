package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.manager.HostPluginRuntime
import com.kigtts.next.plugins.control.STORE_ID
import com.kigtts.plugin.api.PluginContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class BuiltinMarketTest {
    @Test fun opensLocalizedMarketWithLastVerifiedSourceAndNoInstalledPackage() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = HostPluginRuntime.get(app).manager
        val data = manager.paths.storage(STORE_ID)
        data.file("config/sources.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"officialSourceSeeded":true,"resourceSourceSeeded":true,"expressionSourceSeeded":true,"records":[
                {"key":"kept-source","url":"https://example.invalid/index.json","name":"Local source","catalogId":"test.market"}
            ]}""")
        }
        data.file("catalogs/kept-source.json").apply {
            parentFile!!.mkdirs()
            writeText("""{"id":"test.market","name":"Local source","packages":[],"categories":[
                {"id":"plugin.sample","name":"示例工具","localizations":{"en":"Sample tools","ko":"샘플 도구","ja":"サンプルツール"}}
            ]}""")
        }

        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            manager.reload()
            val market = requireNotNull(manager.registry.plugin(STORE_ID))
            assertTrue(STORE_ID in manager.builtinIds)
            assertNull(manager.packages.installed(STORE_ID))
            for ((locale, title, category) in listOf(
                Triple("zh-CN", "拓展市场", "示例工具"),
                Triple("zh-CN-x-casual", "拓展市场", "示例工具"),
                Triple("en-US", "Extension Market", "Sample tools"),
                Triple("ja-JP", "拡張マーケット", "サンプルツール"),
                Triple("ko-KR", "확장 마켓", "샘플 도구"),
            )) {
                val context = PluginContext("", "", locale = locale)
                assertEquals(title, market.contributions(context).settingsEntries.single().label)
                assertEquals(title, market.page("store", context)?.title)
                assertTrue(market.page("categories", context)?.management?.items.orEmpty().any { it.title == category })
            }
        } finally {
            try { manager.close() } finally { Dispatchers.resetMain() }
        }
    }
}
