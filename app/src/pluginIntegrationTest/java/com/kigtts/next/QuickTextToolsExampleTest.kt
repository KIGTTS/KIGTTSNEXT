@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.kigtts.next

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.kigtts.plugin.api.*
import com.kigtts.tools.quicktext.QuickTextToolsPlugin
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class QuickTextToolsExampleTest {
    @Before fun before() {Dispatchers.setMain(UnconfinedTestDispatcher())}
    @After fun after() {Dispatchers.resetMain()}
    @Test fun sdkExampleCreatesARealOwnedCalculatorCardThroughThePublicContract()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>();app.getSharedPreferences("quick-phrases",0).edit().clear().commit()
        val model=testAppModel(app);val plugin=QuickTextToolsPlugin();val owner=plugin.descriptor.id
        plugin.attach(object:PluginEnvironment {
            override val storage=model.pluginManager.paths.storage(owner)
            override val assets=File(requireNotNull(System.getProperty("kig.quick.example.assets")))
            override val quickTextBroker=model.quickTextPlugins.forPlugin(owner,setOf("quick-text-read","quick-text-write"))
            override suspend fun download(url:String,relativePath:String,sha256:String?):File=error("network is not needed")
        })
        val result=plugin.execute("create-calculator",PluginContext("",""));assertTrue(result is PluginOutcome.Message)
        val phrase=model.phrases.value.single {it.metadata["$owner/widget"]=="calculator"};assertEquals("小工具",phrase.category)
        val media=model.mediaRepository.get(phrase.mediaId)!!;assertEquals(owner,media.pluginId);assertNotNull(media.projectRoot)
        val html=model.mediaRepository.file(media)!!.readText();assertTrue(html.contains("将结果显示为字幕"));assertFalse(html.contains("eval("))
        val answer=plugin.execute("calculator/result",PluginContext("","",inputs=mapOf("result" to "4"))) as PluginOutcome.ReplaceEditorText
        assertEquals("4",answer.text)
        val export=plugin.execute("export",PluginContext("","")) as PluginOutcome.ExportResource
        assertTrue(model.pluginManager.paths.storage(owner).file(export.resource.path).isFile)
    }
}
